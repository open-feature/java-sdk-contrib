package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync;

import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelConnector;
import dev.openfeature.contrib.providers.flagd.resolver.common.FlagdProviderEvent;
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
import dev.openfeature.sdk.Awaitable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.stub.StreamObserver;
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
    private final int deadline;
    private final String selector;
    private final String providerId;
    private final boolean syncMetadataDisabled;
    private final ChannelConnector<FlagSyncServiceStub, FlagSyncServiceBlockingStub> channelConnector;
    private final BlockingQueue<QueuePayload> outgoingQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
    private final FlagSyncServiceStub stub;
    private final FlagSyncServiceBlockingStub blockingStub;

    /**
     * Creates a new SyncStreamQueueSource responsible for observing the event stream.
     */
    public SyncStreamQueueSource(final FlagdOptions options, Consumer<FlagdProviderEvent> onConnectionEvent) {
        streamDeadline = options.getStreamDeadlineMs();
        deadline = options.getDeadline();
        selector = options.getSelector();
        providerId = options.getProviderId();
        syncMetadataDisabled = options.isSyncMetadataDisabled();
        channelConnector = new ChannelConnector<>(options, onConnectionEvent);
        this.stub = FlagSyncServiceGrpc.newStub(channelConnector.getChannel()).withWaitForReady();
        this.blockingStub = FlagSyncServiceGrpc.newBlockingStub(channelConnector.getChannel())
                .withWaitForReady();
    }

    // internal use only
    protected SyncStreamQueueSource(
            final FlagdOptions options,
            ChannelConnector<FlagSyncServiceStub, FlagSyncServiceBlockingStub> connectorMock,
            FlagSyncServiceStub stubMock,
            FlagSyncServiceBlockingStub blockingStubMock) {
        streamDeadline = options.getStreamDeadlineMs();
        deadline = options.getDeadline();
        selector = options.getSelector();
        providerId = options.getProviderId();
        channelConnector = connectorMock;
        stub = stubMock;
        syncMetadataDisabled = options.isSyncMetadataDisabled();
        blockingStub = blockingStubMock;
    }

    /** Initialize sync stream connector. */
    public void init() throws Exception {
        channelConnector.initialize();
        Thread listener = new Thread(this::observeSyncStream);
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
    private void observeSyncStream() {
        log.info("Initializing sync stream observer");

        // outer loop for re-issuing the stream request
        // "waitForReady" on the channel, plus our retry policy slow this loop down in error conditions
        while (!shutdown.get()) {
            log.debug("Initializing sync stream request");
            SyncStreamObserver observer = new SyncStreamObserver(outgoingQueue);

            try {
                observer.metadata = getMetadata();
            } catch (Exception metaEx) {
                // retry if getMetadata fails
                String message = metaEx.getMessage();
                log.debug("Metadata request error: {}, will restart", message, metaEx);
                enqueueError(String.format("Error in getMetadata request: %s", message));
                continue;
            }

            try {
                syncFlags(observer);
            } catch (Exception ex) {
                log.error("Unexpected sync stream exception, will restart.", ex);
                enqueueError(String.format("Error in getMetadata request: %s", ex.getMessage()));
            }
        }

        log.info("Shutdown invoked, exiting event stream listener");
    }

    // TODO: remove the metadata call entirely after https://github.com/open-feature/flagd/issues/1584
    private Struct getMetadata() {
        if (syncMetadataDisabled) {
            return null;
        }

        FlagSyncServiceBlockingStub localStub = blockingStub;

        if (deadline > 0) {
            localStub = localStub.withDeadlineAfter(deadline, TimeUnit.MILLISECONDS);
        }

        GetMetadataResponse metadataResponse = localStub.getMetadata(GetMetadataRequest.getDefaultInstance());

        if (metadataResponse != null) {
            return metadataResponse.getMetadata();
        }

        return null;
    }

    private void syncFlags(SyncStreamObserver streamObserver) {
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

        localStub.syncFlags(syncRequest.build(), streamObserver);

        streamObserver.done.await();
    }

    private void enqueueError(String message) {
        enqueueError(outgoingQueue, message);
    }

    private static void enqueueError(BlockingQueue<QueuePayload> queue, String message) {
        if (!queue.offer(new QueuePayload(
                QueuePayloadType.ERROR, message, null))) {
            log.error("Failed to convey ERROR status, queue is full");
        }
    }

    private static class SyncStreamObserver implements StreamObserver<SyncFlagsResponse> {
        private final BlockingQueue<QueuePayload> outgoingQueue;
        private final Awaitable done = new Awaitable();

        private Struct metadata;

        public SyncStreamObserver(BlockingQueue<QueuePayload> outgoingQueue) {
            this.outgoingQueue = outgoingQueue;
        }

        @Override
        public void onNext(SyncFlagsResponse syncFlagsResponse) {
            final String data = syncFlagsResponse.getFlagConfiguration();
            log.debug("Got stream response: {}", data);

            Struct syncContext = syncFlagsResponse.hasSyncContext() ? syncFlagsResponse.getSyncContext() : metadata;

            if (!outgoingQueue.offer(new QueuePayload(QueuePayloadType.DATA, data, syncContext))) {
                log.error("Stream writing failed");
            }
        }

        @Override
        public void onError(Throwable throwable) {
            try {
                String message = throwable != null ? throwable.getMessage() : "unknown";
                log.debug("Stream error: {}, will restart", message, throwable);
                enqueueError(outgoingQueue, String.format("Error from stream: %s", message));
            } finally {
                done.wakeup();
            }
        }

        @Override
        public void onCompleted() {
            log.debug("Sync stream completed, will restart");
            done.wakeup();
        }
    }
}
