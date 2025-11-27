package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync;

import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelBuilder;
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
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements the {@link QueueSource} contract and emit flags obtained from flagd sync gRPC contract.
 */
@Slf4j
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP"},
        justification = "Random is used to generate a variation & flag configurations require exposing")
public class SyncStreamQueueSource implements QueueSource {
    private static final int QUEUE_SIZE = 5;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean shouldThrottle = new AtomicBoolean(false);
    private final int streamDeadline;
    private final int deadline;
    private final int maxBackoffMs;
    private final String selector;
    private final String providerId;
    private final boolean syncMetadataDisabled;
    private final ChannelConnector channelConnector;
    private final BlockingQueue<QueuePayload> outgoingQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
    private final FlagSyncServiceStub flagSyncStub;
    private final FlagSyncServiceBlockingStub metadataStub;
    private final ScheduledExecutorService scheduler;

    /**
     * Creates a new SyncStreamQueueSource responsible for observing the event stream.
     */
    public SyncStreamQueueSource(final FlagdOptions options, Consumer<FlagdProviderEvent> onConnectionEvent) {
        streamDeadline = options.getStreamDeadlineMs();
        deadline = options.getDeadline();
        selector = options.getSelector();
        providerId = options.getProviderId();
        maxBackoffMs = options.getRetryBackoffMaxMs();
        syncMetadataDisabled = options.isSyncMetadataDisabled();
        channelConnector = new ChannelConnector(options, onConnectionEvent, ChannelBuilder.nettyChannel(options));
        flagSyncStub =
                FlagSyncServiceGrpc.newStub(channelConnector.getChannel()).withWaitForReady();
        metadataStub = FlagSyncServiceGrpc.newBlockingStub(channelConnector.getChannel())
                .withWaitForReady();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "flagd-sync-retry-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    // internal use only
    protected SyncStreamQueueSource(
            final FlagdOptions options,
            ChannelConnector connectorMock,
            FlagSyncServiceStub stubMock,
            FlagSyncServiceBlockingStub blockingStubMock) {
        streamDeadline = options.getStreamDeadlineMs();
        deadline = options.getDeadline();
        selector = options.getSelector();
        providerId = options.getProviderId();
        channelConnector = connectorMock;
        maxBackoffMs = options.getRetryBackoffMaxMs();
        flagSyncStub = stubMock;
        syncMetadataDisabled = options.isSyncMetadataDisabled();
        metadataStub = blockingStubMock;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "flagd-sync-retry-scheduler");
            t.setDaemon(true);
            return t;
        });
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
        // Use atomic compareAndSet to ensure shutdown is only executed once
        // This prevents race conditions when shutdown is called from multiple threads
        if (!shutdown.compareAndSet(false, true)) {
            log.debug("Shutdown already in progress or completed");
            return;
        }
        this.scheduler.shutdownNow();
        try {
            this.scheduler.awaitTermination(deadline, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.debug("Scheduler termination was interrupted", e);
            Thread.currentThread().interrupt();
        }
        this.channelConnector.shutdown();
    }

    /** Contains blocking calls, to be used concurrently. */
    private void observeSyncStream() {
        log.info("Initializing sync stream observer");

        // outer loop for re-issuing the stream request
        // "waitForReady" on the channel, plus our retry policy slow this loop down in
        // error conditions
        while (!shutdown.get()) {
            if (shouldThrottle.getAndSet(false)) {
                log.debug("Previous stream ended with error, waiting {} ms before retry", this.maxBackoffMs);
                scheduleRetry();
                return;
            }

            log.debug("Initializing sync stream request");
            SyncStreamObserver observer = new SyncStreamObserver(outgoingQueue, shouldThrottle);
            try {
                observer.metadata = getMetadata();
            } catch (Exception metaEx) {
                // retry if getMetadata fails
                String message = metaEx.getMessage();
                log.debug("Metadata request error: {}, will restart", message, metaEx);
                enqueueError(String.format("Error in getMetadata request: %s", message));
                shouldThrottle.set(true);
                continue;
            }

            try {
                syncFlags(observer);
            } catch (Exception ex) {
                log.error("Unexpected sync stream exception, will restart.", ex);
                enqueueError(String.format("Error in syncStream: %s", ex.getMessage()));
                shouldThrottle.set(true);
            }
        }

        log.info("Shutdown invoked, exiting event stream listener");
    }

    /**
     * Schedules a retry of the sync stream after the backoff period.
     * Uses a non-blocking approach instead of Thread.sleep().
     */
    private void scheduleRetry() {
        if (shutdown.get()) {
            log.info("Shutdown invoked, exiting event stream listener");
            return;
        }
        try {
            scheduler.schedule(this::observeSyncStream, this.maxBackoffMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Scheduler was shut down after the shutdown check, which is fine
            log.debug("Retry scheduling rejected, scheduler is shut down", e);
        }
    }

    // TODO: remove the metadata call entirely after https://github.com/open-feature/flagd/issues/1584
    private Struct getMetadata() {
        if (syncMetadataDisabled) {
            return null;
        }

        FlagSyncServiceBlockingStub localStub = metadataStub;

        if (deadline > 0) {
            localStub = localStub.withDeadlineAfter(deadline, TimeUnit.MILLISECONDS);
        }

        try {
            GetMetadataResponse metadataResponse = localStub.getMetadata(GetMetadataRequest.getDefaultInstance());

            if (metadataResponse != null) {
                return metadataResponse.getMetadata();
            }

            return null;
        } catch (StatusRuntimeException e) {
            // In newer versions of flagd, metadata is part of the sync stream. If the method is unimplemented, we
            // can ignore the error
            if (e.getStatus() != null
                    && Status.Code.UNIMPLEMENTED.equals(e.getStatus().getCode())) {
                return null;
            }

            throw e;
        }
    }

    private void syncFlags(SyncStreamObserver streamObserver) {
        FlagSyncServiceStub localStub = flagSyncStub; // don't mutate the stub
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
        if (!queue.offer(new QueuePayload(QueuePayloadType.ERROR, message, null))) {
            log.error("Failed to convey ERROR status, queue is full");
        }
    }

    private static class SyncStreamObserver implements StreamObserver<SyncFlagsResponse> {
        private final BlockingQueue<QueuePayload> outgoingQueue;
        private final AtomicBoolean shouldThrottle;
        private final Awaitable done = new Awaitable();

        private Struct metadata;

        public SyncStreamObserver(BlockingQueue<QueuePayload> outgoingQueue, AtomicBoolean shouldThrottle) {
            this.outgoingQueue = outgoingQueue;
            this.shouldThrottle = shouldThrottle;
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

                // Set throttling flag to ensure backoff before retry
                this.shouldThrottle.set(true);
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
