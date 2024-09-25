package dev.openfeature.contrib.providers.flagd.resolver.grpc;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelBuilder;
import dev.openfeature.contrib.providers.flagd.resolver.common.Util;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.Cache;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.EventStreamRequest;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.EventStreamResponse;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc;
import dev.openfeature.sdk.internal.TriConsumer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

/**
 * Class that abstracts the gRPC communication with flagd.
 */
@Slf4j
@SuppressFBWarnings(justification = "cache needs to be read and write by multiple objects")
public class GrpcConnector {
    private final Object sync = new Object();
    private final Random random = new Random();

    private final ServiceGrpc.ServiceBlockingStub serviceBlockingStub;
    private final ServiceGrpc.ServiceStub serviceStub;
    private final ManagedChannel channel;
    private final int maxEventStreamRetries;

    private final int startEventStreamRetryBackoff;
    private final long deadline;

    private final Cache cache;
    private final TriConsumer<Boolean, List<String>, Map<String, Object>> onConnectionEvent;
    private final Supplier<Boolean> connectedSupplier;

    private int eventStreamAttempt = 1;
    private int eventStreamRetryBackoff;

    // Thread responsible for event observation
    private Thread eventObserverThread;

    /**
     * GrpcConnector creates an abstraction over gRPC communication.
     * 
     * @param options flagd options
     * @param cache cache to use
     * @param connectedSupplier lambda providing current connection status from caller
     * @param onConnectionEvent lambda which handles changes in the connection/stream
     */
    public GrpcConnector(final FlagdOptions options, final Cache cache, final Supplier<Boolean> connectedSupplier,
            TriConsumer<Boolean, List<String>, Map<String, Object>> onConnectionEvent) {
        this.channel = ChannelBuilder.nettyChannel(options);
        this.serviceStub = ServiceGrpc.newStub(channel);
        this.serviceBlockingStub = ServiceGrpc.newBlockingStub(channel);
        this.maxEventStreamRetries = options.getMaxEventStreamRetries();
        this.startEventStreamRetryBackoff = options.getRetryBackoffMs();
        this.eventStreamRetryBackoff = options.getRetryBackoffMs();
        this.deadline = options.getDeadline();
        this.cache = cache;
        this.onConnectionEvent = onConnectionEvent;
        this.connectedSupplier = connectedSupplier;
    }

    /**
     * Initialize the gRPC stream.
     */
    public void initialize() throws Exception {
        eventObserverThread = new Thread(this::observeEventStream);
        eventObserverThread.setDaemon(true);
        eventObserverThread.start();

        // block till ready
        Util.busyWaitAndCheck(this.deadline, this.connectedSupplier);
    }

    /**
     * Shuts down all gRPC resources.
     *
     * @throws Exception is something goes wrong while terminating the
     *                   communication.
     */
    public void shutdown() throws Exception {
        // first shutdown the event listener
        if (this.eventObserverThread != null) {
            this.eventObserverThread.interrupt();
        }

        try {
            if (this.channel != null && !this.channel.isShutdown()) {
                this.channel.shutdown();
                this.channel.awaitTermination(this.deadline, TimeUnit.MILLISECONDS);
            }
        } finally {
            this.cache.clear();
            if (this.channel != null && !this.channel.isShutdown()) {
                this.channel.shutdownNow();
                this.channel.awaitTermination(this.deadline, TimeUnit.MILLISECONDS);
                log.warn(String.format("Unable to shut down channel by %d deadline", this.deadline));
            }
            this.onConnectionEvent.accept(false, Collections.emptyList(), Collections.emptyMap());
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
     * Event stream observer logic. This contains blocking mechanisms, hence must be
     * run in a dedicated thread.
     */
    private void observeEventStream() {
        while (this.eventStreamAttempt <= this.maxEventStreamRetries) {
            final StreamObserver<EventStreamResponse> responseObserver = new EventStreamObserver(sync, this.cache,
                    this::grpconConnectionEvent);
            this.serviceStub.eventStream(EventStreamRequest.getDefaultInstance(), responseObserver);

            try {
                synchronized (sync) {
                    sync.wait();
                }
            } catch (InterruptedException e) {
                // Interruptions are considered end calls for this observer, hence log and
                // return
                // Note - this is the most common interruption when shutdown, hence the log
                // level debug
                log.debug("interruption while waiting for condition", e);
                Thread.currentThread().interrupt();
            }

            this.eventStreamAttempt++;
            // backoff with a jitter
            this.eventStreamRetryBackoff = 2 * this.eventStreamRetryBackoff + random.nextInt(100);

            try {
                Thread.sleep(this.eventStreamRetryBackoff);
            } catch (InterruptedException e) {
                // Interruptions are considered end calls for this observer, hence log and
                // return
                log.warn("interrupted while restarting gRPC Event Stream");
                Thread.currentThread().interrupt();
            }
        }

        log.error("failed to connect to event stream, exhausted retries");
        this.grpconConnectionEvent(false, Collections.emptyList());
    }

    private void grpcOnConnectionEvent(final boolean connected, final List<String> changedFlags) {
        // reset reconnection states
        if (connected) {
            this.eventStreamAttempt = 1;
            this.eventStreamRetryBackoff = this.startEventStreamRetryBackoff;
        }
        // chain to initiator
        this.onConnectionEvent.accept(connected, changedFlags, Collections.emptyMap());
    }
}
