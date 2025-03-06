package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelConnector;
import dev.openfeature.contrib.providers.flagd.resolver.common.FlagdProviderEvent;
import dev.openfeature.contrib.providers.flagd.resolver.common.QueueingStreamObserver;
import dev.openfeature.contrib.providers.flagd.resolver.common.StreamResponseModel;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc.FlagSyncServiceBlockingStub;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc.FlagSyncServiceStub;
import dev.openfeature.flagd.grpc.sync.Sync.GetMetadataRequest;
import dev.openfeature.flagd.grpc.sync.Sync.GetMetadataResponse;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsRequest;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.Context;
import io.grpc.Context.CancellableContext;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements the {@link QueueSource} contract and emit flags obtained from flagd sync gRPC contract.
 */
@Slf4j
@SuppressFBWarnings(
        value = {"PREDICTABLE_RANDOM", "EI_EXPOSE_REP"},
        justification = "Random is used to generate a variation & flag configurations require exposing")
public class SyncStreamQueueSource implements QueueSource {
    private static final int QUEUE_SIZE = 5;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final int streamDeadline;
    private final String selector;
    private final String providerId;
    private final ChannelConnector<FlagSyncServiceStub, FlagSyncServiceBlockingStub> channelConnector;
    private final LinkedBlockingQueue<StreamResponseModel<SyncFlagsResponse>> incomingQueue =
            new LinkedBlockingQueue<>(QUEUE_SIZE);
    private final BlockingQueue<QueuePayload> outgoingQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
    private final FlagSyncServiceStub stub;

    /**
     * Creates a new SyncStreamQueueSource responsible for observing the event stream.
     */
    public SyncStreamQueueSource(final FlagdOptions options, Consumer<FlagdProviderEvent> onConnectionEvent) {
        streamDeadline = options.getStreamDeadlineMs();
        selector = options.getSelector();
        providerId = options.getProviderId();
        channelConnector = new ChannelConnector<>(options, FlagSyncServiceGrpc::newBlockingStub, onConnectionEvent);
        this.stub = FlagSyncServiceGrpc.newStub(channelConnector.getChannel()).withWaitForReady();
    }

    // internal use only
    protected SyncStreamQueueSource(
            final FlagdOptions options,
            ChannelConnector<FlagSyncServiceStub, FlagSyncServiceBlockingStub> connectorMock,
            FlagSyncServiceStub stubMock) {
        streamDeadline = options.getStreamDeadlineMs();
        selector = options.getSelector();
        providerId = options.getProviderId();
        channelConnector = connectorMock;
        stub = stubMock;
    }

    /** Initialize sync stream connector. */
    public void init() throws Exception {
        channelConnector.initialize();
        Thread listener = new Thread(() -> {
            try {
                observeSyncStream();
            } catch (InterruptedException e) {
                log.warn("gRPC event stream interrupted, flag configurations are stale", e);
                Thread.currentThread().interrupt();
            }
        });

        listener.setDaemon(true);
        listener.start();
    }

    /** Get blocking queue to obtain payloads exposed by this connector. */
    public BlockingQueue<QueuePayload> getStreamQueue() {
        return outgoingQueue;
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
        this.channelConnector.shutdown();
    }

    /** Contains blocking calls, to be used concurrently. */
    private void observeSyncStream() throws InterruptedException {

        log.info("Initializing sync stream observer");

        // outer loop for re-issuing the stream request
        while (!shutdown.get()) {

            log.debug("Initializing sync stream request");
            final GetMetadataRequest.Builder metadataRequest = GetMetadataRequest.newBuilder();
            GetMetadataResponse metadataResponse = GetMetadataResponse.getDefaultInstance();

            // create a context which exists to track and cancel the stream
            try (CancellableContext context = Context.current().withCancellation()) {

                restart(); // start the stream within the context

                try {
                    metadataResponse = channelConnector.getBlockingStub().getMetadata(metadataRequest.build());
                } catch (Exception metaEx) {
                    log.error("Metadata exception: {}, cancelling stream", metaEx.getMessage(), metaEx);
                    context.cancel(metaEx);
                }

                // inner loop for handling messages
                while (!shutdown.get()) {
                    final StreamResponseModel<SyncFlagsResponse> taken = incomingQueue.take();
                    if (taken.isComplete()) {
                        log.debug("Sync stream completed, will reconnect");
                        // The stream is complete, we still try to reconnect
                        break;
                    }

                    Throwable streamException = taken.getError();
                    if (streamException != null) {
                        log.debug("Exception in GRPC connection, streamException {}, will reconnect", streamException);
                        if (!outgoingQueue.offer(new QueuePayload(
                                QueuePayloadType.ERROR, "Error from stream or metadata", metadataResponse))) {
                            log.error("Failed to convey ERROR status, queue is full");
                        }
                        break;
                    }

                    final SyncFlagsResponse flagsResponse = taken.getResponse();
                    final String data = flagsResponse.getFlagConfiguration();
                    log.debug("Got stream response: {}", data);

                    if (!outgoingQueue.offer(new QueuePayload(QueuePayloadType.DATA, data, metadataResponse))) {
                        log.error("Stream writing failed");
                    }
                }
            }
        }

        log.info("Shutdown invoked, exiting event stream listener");
    }

    private void restart() {
        FlagSyncServiceStub localStub = stub; // don't mutate the stub
        if (streamDeadline > 0) {
            localStub = localStub.withDeadlineAfter(streamDeadline, TimeUnit.MILLISECONDS);
        }

        final SyncFlagsRequest.Builder syncRequest = SyncFlagsRequest.newBuilder();
        if (this.selector != null) {
            syncRequest.setSelector(this.selector);
        }

        if (this.providerId != null) {
            syncRequest.setProviderId(this.providerId);
        }

        localStub.syncFlags(syncRequest.build(), new QueueingStreamObserver<SyncFlagsResponse>(incomingQueue));
    }
}
