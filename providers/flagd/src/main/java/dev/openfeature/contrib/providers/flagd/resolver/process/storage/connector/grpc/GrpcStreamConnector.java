package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelBuilder;
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
import io.grpc.Context;
import io.grpc.Context.CancellableContext;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;

/**
 * Implements the {@link Connector} contract and emit flags obtained from flagd
 * sync gRPC contract.
 */
@Slf4j
@SuppressFBWarnings(value = { "PREDICTABLE_RANDOM",
    "EI_EXPOSE_REP" }, justification = "Random is used to generate a variation & flag configurations require exposing")
public class GrpcStreamConnector implements Connector {
    private static final int QUEUE_SIZE = 5;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final BlockingQueue<QueuePayload> blockingQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
    private final ManagedChannel channel;
    private final FlagSyncServiceStub serviceStub;
    private final FlagSyncServiceBlockingStub serviceBlockingStub;
    private final int deadline;
    private final int streamDeadlineMs;
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
        streamDeadlineMs = options.getStreamDeadlineMs();
        selector = options.getSelector();
    }

    /**
     * Initialize gRPC stream connector.
     */
    public void init() {
        Thread listener = new Thread(() -> {
            try {
                observeEventStream(blockingQueue, shutdown, serviceStub, serviceBlockingStub, selector, deadline,
                        streamDeadlineMs);
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
            final String selector,
            final int deadline,
            final int streamDeadlineMs)
            throws InterruptedException {

        final BlockingQueue<GrpcResponseModel> streamReceiver = new LinkedBlockingQueue<>(QUEUE_SIZE);
        final GrpcStreamConnectorBackoffService backoffService = GrpcStreamConnectorBackoffService.create();

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
                FlagSyncServiceStub localServiceStub = serviceStub;
                if (streamDeadlineMs > 0) {
                    localServiceStub = localServiceStub.withDeadlineAfter(streamDeadlineMs, TimeUnit.MILLISECONDS);
                }

                localServiceStub.syncFlags(syncRequest.build(), new GrpcStreamHandler(streamReceiver));

                try {
                    metadataResponse = serviceBlockingStub.withDeadlineAfter(deadline, TimeUnit.MILLISECONDS)
                            .getMetadata(metadataRequest.build());
                } catch (Exception e) {
                    // the chances this call fails but the syncRequest does not are slim
                    // it could be that the server doesn't implement this RPC
                    // instead of logging and throwing here, retain the exception and handle in the
                    // stream logic below
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

                    Throwable streamException = response.getError();
                    if (streamException != null || metadataException != null) {
                        long retryDelay = backoffService.getCurrentBackoffMillis();

                        // if we are in silent recover mode, we should not expose the error to the client
                        if (backoffService.shouldRecoverSilently()) {
                            logExceptions(Level.INFO, streamException, metadataException, retryDelay);
                        } else {
                            logExceptions(Level.ERROR, streamException, metadataException, retryDelay);
                            if (!writeTo.offer(new QueuePayload(QueuePayloadType.ERROR,
                                    "Error from stream or metadata", metadataResponse))) {
                                log.error("Failed to convey ERROR status, queue is full");
                            }
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

                    // reset backoff if we succeeded in a retry attempt
                    backoffService.reset();
                }
            }

            // check for shutdown and avoid sleep
            if (!shutdown.get()) {
                log.debug("Stream failed, retrying in {}ms", backoffService.getCurrentBackoffMillis());
                backoffService.waitUntilNextAttempt();
            }
        }

        log.info("Shutdown invoked, exiting event stream listener");
    }

    private static void logExceptions(Level logLevel, Throwable streamException, Exception metadataException,
                                      long retryDelay) {
        if (streamException != null) {
            log.atLevel(logLevel)
                    .setCause(streamException)
                    .log("Error initializing stream, retrying in {}ms", retryDelay);
        }

        if (metadataException != null) {
            log.atLevel(logLevel)
                    .setCause(metadataException)
                    .log("Error initializing metadata, retrying in {}ms", retryDelay);
        }
    }
}
