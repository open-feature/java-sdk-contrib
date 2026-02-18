package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import static dev.openfeature.contrib.providers.flagd.resolver.common.Convert.convertProtobufMapToStructure;

import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import dev.openfeature.contrib.tools.flagd.api.Evaluator;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.Structure;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/** Feature flag storage. */
@Slf4j
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP"},
        justification = "Feature flag comes as a Json configuration, hence they must be exposed")
public class FlagStore implements Storage {
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final BlockingQueue<StorageStateChange> stateBlockingQueue = new LinkedBlockingQueue<>(4);

    private final QueueSource connector;
    private final Evaluator evaluator;

    public FlagStore(final QueueSource connector, final Evaluator evaluator) {
        this.connector = connector;
        this.evaluator = evaluator;
    }

    /** Initialize storage layer. */
    @Override
    public void init() throws Exception {
        connector.init();
        Thread streamer = new Thread(() -> {
            try {
                streamerListener(connector);
            } catch (InterruptedException e) {
                log.warn("connection listener failed", e);
                Thread.currentThread().interrupt();
            }
        });
        streamer.setDaemon(true);
        streamer.start();
    }

    /**
     * Shutdown storage layer.
     *
     * @throws InterruptedException if stream can't be closed within deadline.
     */
    @Override
    public void shutdown() throws InterruptedException {
        if (shutdown.getAndSet(true)) {
            return;
        }

        connector.shutdown();
    }

    /** Retrieve blocking queue to check storage status. */
    @Override
    public BlockingQueue<StorageStateChange> getStateQueue() {
        return stateBlockingQueue;
    }

    private void streamerListener(final QueueSource connector) throws InterruptedException {
        final BlockingQueue<QueuePayload> streamPayloads = connector.getStreamQueue();

        while (!shutdown.get()) {
            final QueuePayload payload = streamPayloads.take();
            switch (payload.getType()) {
                case DATA:
                    try {
                        // Delegate flag parsing to the evaluator
                        List<String> changedFlagsKeys = evaluator.setFlagsAndGetChangedKeys(payload.getFlagData());
                        Structure syncContext = parseSyncContext(payload.getSyncContext());

                        if (!stateBlockingQueue.offer(
                                new StorageStateChange(StorageState.OK, changedFlagsKeys, syncContext))) {
                            log.warn("Failed to convey OK status, queue is full");
                        }
                    } catch (Throwable e) {
                        // catch all exceptions and avoid stream listener interruptions
                        log.warn("Invalid flag sync payload from connector", e);
                        if (!stateBlockingQueue.offer(new StorageStateChange(StorageState.STALE))) {
                            log.warn("Failed to convey TRANSIENT_ERROR status, queue is full");
                        }
                    }
                    break;
                case ERROR:
                    if (!stateBlockingQueue.offer(new StorageStateChange(StorageState.STALE))) {
                        log.warn("Failed to convey TRANSIENT_ERROR status, queue is full");
                    }
                    break;
                case SHUTDOWN:
                    shutdown();
                    if (!stateBlockingQueue.offer(new StorageStateChange(StorageState.ERROR))) {
                        log.warn("Failed to convey FATAL_ERROR status, queue is full");
                    }
                    break;
                default:
                    log.warn(String.format("Payload with unknown type: %s", payload.getType()));
            }
        }

        log.info("Shutting down store stream listener");
    }

    private Structure parseSyncContext(Struct syncContext) {
        if (syncContext != null) {
            try {
                return convertProtobufMapToStructure(syncContext.getFieldsMap());
            } catch (Exception exception) {
                log.error("Failed to parse metadataResponse, provider metadata may not be up-to-date");
            }
        }
        return new ImmutableStructure();
    }
}
