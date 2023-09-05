package dev.openfeature.contrib.providers.flagd.grpc;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.cache.Cache;
import dev.openfeature.flagd.grpc.Schema;
import dev.openfeature.flagd.grpc.ServiceGrpc;
import dev.openfeature.sdk.ProviderState;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLException;
import java.io.File;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Class that abstracts the gRPC communication with flagd.
 */
@Slf4j
@SuppressFBWarnings(justification = "cache needs to be read and write by multiple objects")
public class GrpcConnector {
    private final Object sync = new Object();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final Random random = new Random();
    private final int maxJitter = 100;

    private final ServiceGrpc.ServiceBlockingStub serviceBlockingStub;
    private final ServiceGrpc.ServiceStub serviceStub;
    private final ManagedChannel channel;
    private final int maxEventStreamRetries;

    private final int startEventStreamRetryBackoff;
    private final long deadline;

    private final Cache cache;
    private final Consumer<ProviderState> stateConsumer;

    private int eventStreamAttempt = 1;
    private int eventStreamRetryBackoff;

    // Thread responsible for event observation
    private Thread eventObserverThread;

    /**
     * GrpcConnector creates an abstraction over gRPC communication.
     *
     * @param options       options to build the gRPC channel.
     * @param cache         cache to use.
     * @param stateConsumer lambda to call for setting the state.
     */
    public GrpcConnector(final FlagdOptions options, final Cache cache, Consumer<ProviderState> stateConsumer) {
        this.channel = nettyChannel(options);
        this.serviceStub = ServiceGrpc.newStub(channel);
        this.serviceBlockingStub = ServiceGrpc.newBlockingStub(channel);

        this.maxEventStreamRetries = options.getMaxEventStreamRetries();
        this.startEventStreamRetryBackoff = options.getRetryBackoffMs();
        this.eventStreamRetryBackoff = options.getRetryBackoffMs();
        this.deadline = options.getDeadline();
        this.cache = cache;
        this.stateConsumer = stateConsumer;
    }

    /**
     * Initialize the gRPC stream.
     */
    public void initialize() throws Exception {
        eventObserverThread = new Thread(this::observeEventStream);
        eventObserverThread.start();

        // block till ready
        busyWaitAndCheck(this.deadline, this.connected);
    }

    /**
     * Shuts down all gRPC resources.
     *
     * @throws Exception is something goes wrong while terminating the communication.
     */
    public void shutdown() throws Exception {
        // first shutdown the event listener
        if (this.eventObserverThread != null) {
            this.eventObserverThread.interrupt();
        }

        try {
            if (this.channel != null) {
                this.channel.shutdown();
                this.channel.awaitTermination(5, TimeUnit.SECONDS);
            }
        } finally {
            this.cache.clear();
            if (this.channel != null) {
                this.channel.shutdownNow();
            }
        }
    }

    /**
     * Provide the object that can be used to resolve Feature Flag values.
     *
     * @return a {@link ServiceGrpc.ServiceBlockingStub} for running FF resolution.
     */
    public ServiceGrpc.ServiceBlockingStub getResolver() {
        return serviceBlockingStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS);
    }

    /**
     * Event stream observer logic. This contains blocking mechanisms, hence must be run in a dedicated thread.
     */
    private void observeEventStream() {
        while (this.eventStreamAttempt <= this.maxEventStreamRetries) {
            final StreamObserver<Schema.EventStreamResponse> responseObserver =
                    new EventStreamObserver(sync, this.cache, this::grpcStateConsumer);
            this.serviceStub.eventStream(Schema.EventStreamRequest.getDefaultInstance(), responseObserver);

            try {
                synchronized (sync) {
                    sync.wait();
                }
            } catch (InterruptedException e) {
                // Interruptions are considered end calls for this observer, hence log and return
                // Note - this is the most common interruption when shutdown, hence the log level debug
                log.debug("interruption while waiting for condition", e);
                return;
            }

            this.eventStreamAttempt++;
            // backoff with a jitter
            this.eventStreamRetryBackoff = 2 * this.eventStreamRetryBackoff + random.nextInt(maxJitter);

            try {
                Thread.sleep(this.eventStreamRetryBackoff);
            } catch (InterruptedException e) {
                // Interruptions are considered end calls for this observer, hence log and return
                log.warn("interrupted while restarting gRPC Event Stream");
                return;
            }
        }

        log.error("failed to connect to event stream, exhausted retries");
        this.grpcStateConsumer(ProviderState.ERROR);
    }

    private void grpcStateConsumer(final ProviderState state) {
        // check for readiness
        if (ProviderState.READY.equals(state)) {
            this.eventStreamAttempt = 1;
            this.eventStreamRetryBackoff = this.startEventStreamRetryBackoff;
            this.connected.set(true);
        } else if (ProviderState.ERROR.equals(state)) {
            // reset connection status
            this.connected.set(false);
        }

        // chain to initiator
        this.stateConsumer.accept(state);
    }

    /**
     * A helper to block the caller for given conditions.
     *
     * @param deadline number of milliseconds to block
     * @param check    {@link AtomicBoolean} to check for status true
     */
    private static void busyWaitAndCheck(final Long deadline, final AtomicBoolean check) throws InterruptedException {
        long start = System.currentTimeMillis();

        do {
            if (deadline <= System.currentTimeMillis() - start) {
                throw new RuntimeException(String.format("Initialization not complete after %d ms", deadline));
            }

            Thread.sleep(50L);
        } while (!check.get());
    }

    /**
     * This method is a helper to build a {@link ManagedChannel} from provided {@link FlagdOptions}.
     */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "certificate path is a user input")
    private static ManagedChannel nettyChannel(final FlagdOptions options) {
        // we have a socket path specified, build a channel with a unix socket
        if (options.getSocketPath() != null) {
            // check epoll availability
            if (!Epoll.isAvailable()) {
                throw new IllegalStateException("unix socket cannot be used", Epoll.unavailabilityCause());
            }

            return NettyChannelBuilder
                    .forAddress(new DomainSocketAddress(options.getSocketPath()))
                    .eventLoopGroup(new EpollEventLoopGroup())
                    .channelType(EpollDomainSocketChannel.class)
                    .usePlaintext()
                    .build();
        }

        // build a TCP socket
        try {
            final NettyChannelBuilder builder = NettyChannelBuilder.forAddress(options.getHost(), options.getPort());
            if (options.isTls()) {
                SslContextBuilder sslContext = GrpcSslContexts.forClient();

                if (options.getCertPath() != null) {
                    final File file = new File(options.getCertPath());
                    if (file.exists()) {
                        sslContext.trustManager(file);
                    }
                }

                builder.sslContext(sslContext.build());
            } else {
                builder.usePlaintext();
            }

            // telemetry interceptor if option is provided
            if (options.getOpenTelemetry() != null) {
                builder.intercept(new FlagdGrpcInterceptor(options.getOpenTelemetry()));
            }

            return builder.build();
        } catch (SSLException ssle) {
            SslConfigException sslConfigException = new SslConfigException("Error with SSL configuration.");
            sslConfigException.initCause(ssle);
            throw sslConfigException;
        }
    }
}
