package dev.openfeature.contrib.providers.flagd.grpc;

import dev.openfeature.contrib.providers.flagd.FlagdCache;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.flagd.grpc.Schema;
import dev.openfeature.flagd.grpc.ServiceGrpc;
import dev.openfeature.sdk.ProviderState;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLException;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Class that abstracts the gRPC communication with flagd.
 */
@Slf4j
public class GrpcConnector {

    private final ServiceGrpc.ServiceBlockingStub serviceBlockingStub;
    private final ServiceGrpc.ServiceStub serviceStub;
    private final ManagedChannel channel;
    private final int maxEventStreamRetries;

    private int eventStreamAttempt = 1;
    private int eventStreamRetryBackoff;
    private final int startEventStreamRetryBackoff;
    private final long deadline;

    private final FlagdCache cache;
    private final Consumer<ProviderState> stateConsumer;

    /**
     * GrpcConnector creates an abstraction over gRPC communication.
     * @param options options to build the gRPC channel.
     * @param cache cache to use.
     * @param stateConsumer lambda to call for setting the state.
     */
    public GrpcConnector(final FlagdOptions options, final FlagdCache cache, Consumer<ProviderState> stateConsumer) {
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
     * @throws RuntimeException if the connection cannot be established.
     */
    public void initialize() throws RuntimeException {
        try {
            // try a dummy request
            this.serviceBlockingStub
                    .withWaitForReady()
                    .withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)
                    .resolveBoolean(Schema.ResolveBooleanRequest.newBuilder().setFlagKey("ready?").build());
        } catch (StatusRuntimeException e) {
            // only return the exception if we don't meet the deadline
            if (Status.DEADLINE_EXCEEDED.equals(e.getStatus())) {
                throw e;
            }
        } finally {
            // try in background to open the event stream
            this.startEventStream();
        }
    }

    /**
     * Shuts down all gRPC resources.
     * @throws Exception is something goes wrong while terminating the communication.
     */
    public void shutdown() throws Exception {
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
     * Resets internal state for retrying establishing the stream.
     */
    public void resetRetryConnection() {
        this.eventStreamAttempt = 1;
        this.eventStreamRetryBackoff = this.startEventStreamRetryBackoff;
    }

    /**
     * Provide the object that can be used to resolve Feature Flag values.
     * @return a {@link ServiceGrpc.ServiceBlockingStub} for running FF resolution.
     */
    public ServiceGrpc.ServiceBlockingStub getResolver() {
        return serviceBlockingStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS);
    }

    private void startEventStream() {
        StreamObserver<Schema.EventStreamResponse> responseObserver =
                new EventStreamObserver(this.cache, this.stateConsumer, this::restartEventStream);
        this.serviceStub
                .eventStream(Schema.EventStreamRequest.getDefaultInstance(), responseObserver);
    }

    private void restartEventStream() {
        this.eventStreamAttempt++;
        if (this.eventStreamAttempt > this.maxEventStreamRetries) {
            log.error("failed to connect to event stream, exhausted retries");
            this.stateConsumer.accept(ProviderState.ERROR);
            return;
        }
        this.eventStreamRetryBackoff = 2 * this.eventStreamRetryBackoff;
        try {
            Thread.sleep(this.eventStreamRetryBackoff);
        } catch (InterruptedException e) {
            log.debug("Failed to sleep while restarting gRPC Event Stream");
        }
        this.startEventStream();
    }

    /**
     * This method is a helper to build a {@link ManagedChannel} from provided {@link FlagdOptions}.
     */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "certificate path is a user input")
    private static ManagedChannel nettyChannel(final FlagdOptions options) {
        // we have a socket path specified, build a channel with a unix socket
        if (options.getSocketPath() != null) {
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
