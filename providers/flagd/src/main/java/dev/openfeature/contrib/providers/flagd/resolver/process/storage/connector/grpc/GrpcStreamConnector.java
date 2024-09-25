package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc;

import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelBuilder;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.GrpcResolver;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc.FlagSyncServiceBlockingStub;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc.FlagSyncServiceStub;
import dev.openfeature.flagd.grpc.sync.Sync.GetMetadataRequest;
import dev.openfeature.flagd.grpc.sync.Sync.GetMetadataResponse;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsRequest;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements the {@link Connector} contract and emit flags obtained from flagd
 * sync gRPC contract.
 */
@Slf4j
@SuppressFBWarnings(value = { "PREDICTABLE_RANDOM",
    "EI_EXPOSE_REP" }, justification = "Random is used to generate a variation & flag configurations require exposing")
public class GrpcStreamConnector implements Connector {
    private static final Random RANDOM = new Random();

    private static final int INIT_BACK_OFF = 2 * 1000;
    private static final int MAX_BACK_OFF = 120 * 1000;

    private static final int QUEUE_SIZE = 5;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final BlockingQueue<QueuePayload> blockingQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);

    private final ManagedChannel channel;
    private final FlagSyncServiceStub serviceStub;
    private final FlagSyncServiceBlockingStub serviceBlockingStub;
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
        serviceBlockingStub = FlagSyncServiceGrpc.newBlockingStub(channel);
        deadline = options.getDeadline();
        selector = options.getSelector();
    }

    /**
     * Initialize gRPC stream connector.
     */
    public void init() {
        Thread listener = new Thread(() -> {
            try {
                observeEventStream(blockingQueue, shutdown, serviceStub, serviceBlockingStub, selector);
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
    public BlockingQueue<QueuePayload> getStream() {
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
    static void observeEventStream(final BlockingQueue<QueuePayload> writeTo,
            final AtomicBoolean shutdown,
            final FlagSyncServiceStub serviceStub,
            final FlagSyncServiceBlockingStub serviceBlockingStub,
            final String selector)
            throws InterruptedException {

        final BlockingQueue<GrpcResponseModel> streamReceiver = new LinkedBlockingQueue<>(QUEUE_SIZE);
        int retryDelay = INIT_BACK_OFF;

        log.info("Initializing sync stream observer");

        while (!shutdown.get()) {
            Exception metadataException = null;
            log.debug("Initializing sync stream request");
            final SyncFlagsRequest.Builder syncRequest = SyncFlagsRequest.newBuilder();
            final GetMetadataRequest.Builder metadataRequest = GetMetadataRequest.newBuilder();
            Map<String, Object> metadata = Collections.emptyMap();

            if (selector != null) {
                syncRequest.setSelector(selector);
            }

            serviceStub.syncFlags(syncRequest.build(), new GrpcStreamHandler(streamReceiver));
            try {
                GetMetadataResponse metadataResponse = serviceBlockingStub.getMetadata(metadataRequest.build());
                metadata = GrpcResolver
                        .convertProtobufMapToStructure(metadataResponse.getMetadata().getFieldsMap()).asObjectMap();
            } catch (Exception e) {
                // the chances this call fails but the syncRequest does not are slim
                // it could be that the server doesn't implement this RPC
                // instead of logging here, retain the exception and only log if the
                // streamReceiver doesn't error
                metadataException = e;
            }

            while (!shutdown.get()) {
                final GrpcResponseModel response = streamReceiver.take();

                if (response.isComplete()) {
                    log.info("Sync stream completed");
                    // The stream is complete, this isn't really an error but we should try to
                    // reconnect
                    break;
                }

                if (response.getError() != null) {
                    log.error(String.format("Error from grpc connection, retrying in %dms", retryDelay),
                            response.getError());

                    if (!writeTo.offer(
                            new QueuePayload(QueuePayloadType.ERROR, "Error from stream connection, retrying",
                                    metadata))) {
                        log.error("Failed to convey ERROR status, queue is full");
                    }
                    break;
                }

                final SyncFlagsResponse flagsResponse = response.getSyncFlagsResponse();
                String data = flagsResponse.getFlagConfiguration();
                log.debug("Got stream response: " + data);

                if (!writeTo.offer(
                        new QueuePayload(QueuePayloadType.DATA, data, metadata))) {
                    log.error("Stream writing failed");
                }

                if (metadataException != null) {
                    // if we somehow are connected but the metadata call failed, something strange
                    // happened
                    log.error("Stream connected but getMetadata RPC failed", metadataException);
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
            log.warn(String.format("Stream failed, retrying in %dms", retryDelay));
            Thread.sleep(retryDelay + RANDOM.nextInt(INIT_BACK_OFF));

            if (retryDelay < MAX_BACK_OFF) {
                retryDelay = 2 * retryDelay;
            }
        }

        // log as this can happen after awakened from backoff sleep
        log.info("Shutdown invoked, exiting event stream listener");
    }

}
