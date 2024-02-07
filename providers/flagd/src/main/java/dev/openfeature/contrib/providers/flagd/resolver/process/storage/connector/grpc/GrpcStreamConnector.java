package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelBuilder;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayloadType;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc.FlagSyncServiceStub;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsRequest;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implements the {@link Connector} contract and emit flags obtained from flagd sync gRPC contract.
 */
@Slf4j
@SuppressFBWarnings(value = {"PREDICTABLE_RANDOM", "EI_EXPOSE_REP"},
        justification = "Random is used to generate a variation & flag configurations require exposing")
public class GrpcStreamConnector implements Connector {
    private static final Random RANDOM = new Random();

    private static final int INIT_BACK_OFF = 2 * 1000;
    private static final int MAX_BACK_OFF = 120 * 1000;

    private static final int QUEUE_SIZE = 5;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final BlockingQueue<StreamPayload> blockingQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);

    private final ManagedChannel channel;
    private final FlagSyncServiceGrpc.FlagSyncServiceStub serviceStub;
    private final int deadline;
    private final String selector;

    /**
     * Construct a new GrpcStreamConnector.
     *
     * @param options flagd options
     */
    public GrpcStreamConnector(final FlagdOptions options) {
        channel = ChannelBuilder.nettyChannel(options);
        serviceStub = FlagSyncServiceGrpc.newStub(channel);
        deadline = options.getDeadline();
        selector = options.getSelector();
    }

    /**
     * Initialize gRPC stream connector.
     */
    public void init() {
        Thread listener = new Thread(() -> {
            try {
                final SyncFlagsRequest.Builder requestBuilder = SyncFlagsRequest.newBuilder();

                if (selector != null) {
                    requestBuilder.setSelector(selector);
                }

                observeEventStream(blockingQueue, shutdown, serviceStub, requestBuilder.build());
            } catch (InterruptedException e) {
                log.warn("gRPC event stream interrupted, flag configurations are stale", e);
                Thread.currentThread().interrupt();
            }
        });

        listener.setDaemon(true);
        listener.start();
    }

    /**
     * Get blocking queue to obtain payloads exposed by this connector.
     */
    public BlockingQueue<StreamPayload> getStream() {
        return blockingQueue;
    }

    /**
     * Shutdown gRPC stream connector.
     *
     * @throws InterruptedException if stream can't be closed within deadline.
     */
    public void shutdown() throws InterruptedException {
        if (shutdown.getAndSet(true)) {
            return;
        }

        try {
            if (this.channel != null && !this.channel.isShutdown()) {
                this.channel.shutdown();
                this.channel.awaitTermination(this.deadline, TimeUnit.MILLISECONDS);
            }
        } finally {
            if (this.channel != null && !this.channel.isShutdown()) {
                this.channel.shutdownNow();
                this.channel.awaitTermination(this.deadline, TimeUnit.MILLISECONDS);
                log.warn(String.format("Unable to shut down channel by %d deadline", this.deadline));
            }
        }
    }

    /**
     * Contains blocking calls, to be used concurrently.
     */
    static void observeEventStream(final BlockingQueue<StreamPayload> writeTo,
                                   final AtomicBoolean shutdown,
                                   final FlagSyncServiceStub serviceStub,
                                   final SyncFlagsRequest request)
            throws InterruptedException {

        final BlockingQueue<GrpcResponseModel> streamReceiver = new LinkedBlockingQueue<>(QUEUE_SIZE);
        int retryDelay = INIT_BACK_OFF;

        while (!shutdown.get()) {
            serviceStub.syncFlags(request, new GrpcStreamHandler(streamReceiver));

            while (!shutdown.get()) {
                final GrpcResponseModel response = streamReceiver.take();

                if (response.isComplete()) {
                    // The stream is complete. This is not considered as an error
                    break;
                }

                if (response.getError() != null) {
                    log.warn(String.format("Error from grpc connection, retrying in %dms", retryDelay),
                            response.getError());

                    if (!writeTo.offer(
                            new StreamPayload(StreamPayloadType.ERROR, "Error from stream connection, retrying"))) {
                        log.warn("Failed to convey ERROR satus, queue is full");
                    }
                    break;
                }

                final SyncFlagsResponse flagsResponse = response.getSyncFlagsResponse();
                if (!writeTo.offer(
                        new StreamPayload(StreamPayloadType.DATA, flagsResponse.getFlagConfiguration()))) {
                    log.warn("Stream writing failed");
                }

                // reset retry delay if we succeeded in a retry attempt
                retryDelay = INIT_BACK_OFF;
            }

            // check for shutdown and avoid sleep
            if (shutdown.get()) {
                log.info("Shutdown invoked, exiting event stream listener");
                return;
            }

            // busy wait till next attempt
            Thread.sleep(retryDelay + RANDOM.nextInt(INIT_BACK_OFF));

            if (retryDelay < MAX_BACK_OFF) {
                retryDelay = 2 * retryDelay;
            }
        }

        // log as this can happen after awakened from backoff sleep
        log.info("Shutdown invoked, exiting event stream listener");
    }
}
