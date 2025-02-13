package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.FlagdProviderEvent;
import dev.openfeature.contrib.providers.flagd.resolver.common.GrpcConnector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc;
import dev.openfeature.flagd.grpc.sync.Sync.GetMetadataRequest;
import dev.openfeature.flagd.grpc.sync.Sync.GetMetadataResponse;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsRequest;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.Context;
import io.grpc.Context.CancellableContext;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements the {@link Connector} contract and emit flags obtained from flagd sync gRPC contract.
 */
@Slf4j
@SuppressFBWarnings(
        value = {"PREDICTABLE_RANDOM", "EI_EXPOSE_REP"},
        justification = "Random is used to generate a variation & flag configurations require exposing")
public class GrpcStreamConnector implements Connector {
    private static final int QUEUE_SIZE = 5;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final BlockingQueue<QueuePayload> blockingQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
    private final int deadline;
    private final String selector;
    private final GrpcConnector<
                    FlagSyncServiceGrpc.FlagSyncServiceStub, FlagSyncServiceGrpc.FlagSyncServiceBlockingStub>
            grpcConnector;
    private final LinkedBlockingQueue<GrpcResponseModel> streamReceiver;

    /**
     * Creates a new GrpcStreamConnector responsible for observing the event stream.
     */
    public GrpcStreamConnector(final FlagdOptions options, Consumer<FlagdProviderEvent> onConnectionEvent) {
        deadline = options.getDeadline();
        selector = options.getSelector();
        streamReceiver = new LinkedBlockingQueue<>(QUEUE_SIZE);
        grpcConnector = new GrpcConnector<>(
                options,
                FlagSyncServiceGrpc::newStub,
                FlagSyncServiceGrpc::newBlockingStub,
                onConnectionEvent,
                stub -> stub.syncFlags(SyncFlagsRequest.getDefaultInstance(), new GrpcStreamHandler(streamReceiver)));
    }

    /** Initialize gRPC stream connector. */
    public void init() throws Exception {
        grpcConnector.initialize();
        Thread listener = new Thread(() -> {
            try {
                observeEventStream(blockingQueue, shutdown, deadline);
            } catch (InterruptedException e) {
                log.warn("gRPC event stream interrupted, flag configurations are stale", e);
                Thread.currentThread().interrupt();
            }
        });

        listener.setDaemon(true);
        listener.start();
    }

    /** Get blocking queue to obtain payloads exposed by this connector. */
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
        this.grpcConnector.shutdown();
    }

    /** Contains blocking calls, to be used concurrently. */
    void observeEventStream(final BlockingQueue<QueuePayload> writeTo, final AtomicBoolean shutdown, final int deadline)
            throws InterruptedException {

        log.info("Initializing sync stream observer");

        while (!shutdown.get()) {
            writeTo.clear();
            Exception metadataException = null;

            log.debug("Initializing sync stream request");
            final SyncFlagsRequest.Builder syncRequest = SyncFlagsRequest.newBuilder();
            final GetMetadataRequest.Builder metadataRequest = GetMetadataRequest.newBuilder();
            GetMetadataResponse metadataResponse = GetMetadataResponse.getDefaultInstance();

            if (selector != null) {
                syncRequest.setSelector(selector);
            }

            try (CancellableContext context = Context.current().withCancellation()) {

                try {
                    metadataResponse = grpcConnector.getResolver().getMetadata(metadataRequest.build());
                } catch (Exception e) {
                    // the chances this call fails but the syncRequest does not are slim
                    // it could be that the server doesn't implement this RPC
                    // instead of logging and throwing here, retain the exception and handle in the
                    // stream logic below
                    metadataException = e;
                    log.debug("Metadata exception: {}", e.getMessage(), e);
                }

                while (!shutdown.get()) {
                    final GrpcResponseModel response = streamReceiver.take();
                    if (response.isComplete()) {
                        log.info("Sync stream completed");
                        // The stream is complete, this isn't really an error, but we should try to
                        // reconnect
                        break;
                    }

                    Throwable streamException = response.getError();
                    if (streamException != null || metadataException != null) {
                        log.debug(
                                "Exception in GRPC connection, streamException {}, metadataException {}",
                                streamException,
                                metadataException);
                        if (!writeTo.offer(new QueuePayload(
                                QueuePayloadType.ERROR, "Error from stream or metadata", metadataResponse))) {
                            log.error("Failed to convey ERROR status, queue is full");
                        }

                        // close the context to cancel the stream in case just the metadata call failed
                        context.cancel(metadataException);
                        break;
                    }

                    final SyncFlagsResponse flagsResponse = response.getSyncFlagsResponse();
                    final String data = flagsResponse.getFlagConfiguration();
                    log.debug("Got stream response: {}", data);

                    if (!writeTo.offer(new QueuePayload(QueuePayloadType.DATA, data, metadataResponse))) {
                        log.error("Stream writing failed");
                    }
                }
            }
        }

        log.info("Shutdown invoked, exiting event stream listener");
    }
}
