package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync;

import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelBuilder;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelConnector;
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
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements the {@link QueueSource} contract and emit flags obtained from
 * flagd sync gRPC contract.
 */
@Slf4j
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP"},
        justification = "We need to expose the BlockingQueue to allow consumers to read from it")
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
    private final boolean reinitializeOnError;
    private final FlagdOptions options;
    private final BlockingQueue<QueuePayload> outgoingQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
    private final List<String> fatalStatusCodes;
    private volatile GrpcComponents grpcComponents;

    /**
     * Container for gRPC components to ensure atomicity during reinitialization.
     * All three components are updated together to prevent consumers from seeing
     * an inconsistent state where components are from different channel instances.
     */
    private static class GrpcComponents {
        final ChannelConnector channelConnector;
        final FlagSyncServiceStub flagSyncStub;
        final FlagSyncServiceBlockingStub metadataStub;

        GrpcComponents(ChannelConnector connector, FlagSyncServiceStub stub, FlagSyncServiceBlockingStub blockingStub) {
            this.channelConnector = connector;
            this.flagSyncStub = stub;
            this.metadataStub = blockingStub;
        }
    }

    /**
     * Creates a new SyncStreamQueueSource responsible for observing the event
     * stream.
     */
    public SyncStreamQueueSource(final FlagdOptions options) {
        streamDeadline = options.getStreamDeadlineMs();
        deadline = options.getDeadline();
        selector = options.getSelector();
        providerId = options.getProviderId();
        maxBackoffMs = options.getRetryBackoffMaxMs();
        syncMetadataDisabled = options.isSyncMetadataDisabled();
        fatalStatusCodes = options.getFatalStatusCodes();
        reinitializeOnError = options.isReinitializeOnError();
        this.options = options;
        initializeChannelComponents();
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
        maxBackoffMs = options.getRetryBackoffMaxMs();
        syncMetadataDisabled = options.isSyncMetadataDisabled();
        fatalStatusCodes = options.getFatalStatusCodes();
        reinitializeOnError = options.isReinitializeOnError();
        this.options = options;
        this.grpcComponents = new GrpcComponents(connectorMock, stubMock, blockingStubMock);
    }

    /** Initialize channel connector and stubs. */
    private synchronized void initializeChannelComponents() {
        ChannelConnector newConnector = new ChannelConnector(options, ChannelBuilder.nettyChannel(options));
        FlagSyncServiceStub newFlagSyncStub =
                FlagSyncServiceGrpc.newStub(newConnector.getChannel()).withWaitForReady();
        FlagSyncServiceBlockingStub newMetadataStub =
                FlagSyncServiceGrpc.newBlockingStub(newConnector.getChannel()).withWaitForReady();

        // atomic assignment of all components as a single unit
        grpcComponents = new GrpcComponents(newConnector, newFlagSyncStub, newMetadataStub);
    }

    /** Reinitialize channel connector and stubs on error. */
    public synchronized void reinitializeChannelComponents() {
        if (!reinitializeOnError || shutdown.get()) {
            return;
        }

        log.info("Reinitializing channel gRPC components in attempt to restore stream.");
        GrpcComponents oldComponents = grpcComponents;

        try {
            // create new channel components first
            initializeChannelComponents();
        } catch (Exception e) {
            log.error("Failed to reinitialize channel components", e);
            return;
        }

        // shutdown old connector after successful reinitialization
        if (oldComponents != null && oldComponents.channelConnector != null) {
            try {
                oldComponents.channelConnector.shutdown();
            } catch (Exception e) {
                log.debug("Error shutting down old channel connector during reinitialization", e);
            }
        }
    }

    /** Initialize sync stream connector. */
    public void init() throws Exception {
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
        // Do not enqueue errors from here, as this method can be called externally, causing multiple shutdown signals
        // Use atomic compareAndSet to ensure shutdown is only executed once
        // This prevents race conditions when shutdown is called from multiple threads
        if (!shutdown.compareAndSet(false, true)) {
            log.debug("Shutdown already in progress or completed");
            return;
        }

        grpcComponents.channelConnector.shutdown();
    }

    /** Contains blocking calls, to be used concurrently. */
    private void observeSyncStream() {
        log.info("Initializing sync stream observer");

        // outer loop for re-issuing the stream request
        // "waitForReady" on the channel, plus our retry policy slow this loop down in
        // error conditions
        while (!shutdown.get()) {
            try {
                if (shouldThrottle.getAndSet(false)) {
                    log.debug("Previous stream ended with error, waiting {} ms before retry", this.maxBackoffMs);
                    Thread.sleep(this.maxBackoffMs);

                    // Check shutdown again after sleep to avoid unnecessary work
                    if (shutdown.get()) {
                        break;
                    }
                }

                log.debug("Initializing sync stream request");
                SyncStreamObserver observer = new SyncStreamObserver(outgoingQueue);
                try {
                    observer.metadata = getMetadata();
                } catch (StatusRuntimeException metaEx) {
                    if (fatalStatusCodes.contains(metaEx.getStatus().getCode().name())) {
                        log.info(
                                "Fatal status code for metadata request: {}, not retrying",
                                metaEx.getStatus().getCode());
                        shutdown();
                        enqueue(QueuePayload.SHUTDOWN);
                    } else {
                        // retry for other status codes
                        String message = metaEx.getMessage();
                        log.debug("Metadata request error: {}, will restart", message, metaEx);
                        enqueue(QueuePayload.ERROR);
                    }
                    shouldThrottle.set(true);
                    continue;
                }

                try {
                    syncFlags(observer);
                    handleObserverError(observer);
                } catch (StatusRuntimeException ex) {
                    if (fatalStatusCodes.contains(ex.getStatus().getCode().name())) {
                        log.info(
                                "Fatal status code during sync stream: {}, not retrying",
                                ex.getStatus().getCode());
                        shutdown();
                        enqueue(QueuePayload.SHUTDOWN);
                    } else {
                        // retry for other status codes
                        log.error("Unexpected sync stream exception, will restart.", ex);
                        enqueue(QueuePayload.ERROR);
                    }
                    shouldThrottle.set(true);
                }
            } catch (InterruptedException ie) {
                log.debug("Stream loop interrupted, most likely shutdown was invoked", ie);
            }
        }

        log.info("Shutdown invoked, exiting event stream listener");
    }

    // TODO: remove the metadata call entirely after
    // https://github.com/open-feature/flagd/issues/1584
    private Struct getMetadata() {
        if (syncMetadataDisabled) {
            return null;
        }

        FlagSyncServiceBlockingStub localStub = grpcComponents.metadataStub;

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
            // In newer versions of flagd, metadata is part of the sync stream. If the
            // method is unimplemented, we
            // can ignore the error
            if (e.getStatus() != null
                    && Status.Code.UNIMPLEMENTED.equals(e.getStatus().getCode())) {
                return null;
            }

            throw e;
        }
    }

    private void syncFlags(SyncStreamObserver streamObserver) {
        FlagSyncServiceStub localStub = grpcComponents.flagSyncStub; // don't mutate the stub
        if (streamDeadline > 0) {
            localStub = localStub.withDeadlineAfter(streamDeadline, TimeUnit.MILLISECONDS);
        }

        final SyncFlagsRequest.Builder syncRequest = SyncFlagsRequest.newBuilder();
        // Selector is now passed via header using ClientInterceptor (see constructor)
        // Keeping this for backward compatibility with older flagd versions
        if (this.selector != null) {
            syncRequest.setSelector(this.selector);
        }

        if (this.providerId != null) {
            syncRequest.setProviderId(this.providerId);
        }

        localStub.syncFlags(syncRequest.build(), streamObserver);

        streamObserver.done.await();
    }

    private void handleObserverError(SyncStreamObserver observer) throws InterruptedException {
        if (observer.throwable == null) {
            return;
        }

        Throwable throwable = observer.throwable;
        Status status = Status.fromThrowable(throwable);
        String message = throwable.getMessage();
        if (fatalStatusCodes.contains(status.getCode().name())) {
            shutdown();
        } else {
            log.debug("Stream error: {}, will restart", message, throwable);
            enqueue(QueuePayload.ERROR);
        }

        // Set throttling flag to ensure backoff before retry
        this.shouldThrottle.set(true);
    }

    private void enqueue(QueuePayload queuePayload) {
        if (!outgoingQueue.offer(queuePayload)) {
            log.error("Failed to convey {} status, queue is full", queuePayload.getType());
        }
    }

    private static class SyncStreamObserver implements StreamObserver<SyncFlagsResponse> {
        private final BlockingQueue<QueuePayload> outgoingQueue;
        private final Awaitable done = new Awaitable();

        private Struct metadata;
        private Throwable throwable;

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
            log.debug("Sync stream error received", throwable);
            this.throwable = throwable;
            done.wakeup();
        }

        @Override
        public void onCompleted() {
            log.debug("Sync stream completed, will restart");
            done.wakeup();
        }
    }
}
