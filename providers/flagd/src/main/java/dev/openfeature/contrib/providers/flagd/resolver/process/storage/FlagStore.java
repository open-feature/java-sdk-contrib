package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import static dev.openfeature.contrib.providers.flagd.resolver.common.Convert.convertProtobufMapToStructure;

import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FlagParser;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.ParsingResult;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.Structure;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/** Feature flag storage. */
@Slf4j
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP"},
        justification = "Feature flag comes as a Json configuration, hence they must be exposed")
public class FlagStore implements Storage {
    private final ReentrantReadWriteLock sync = new ReentrantReadWriteLock();
    private final ReadLock readLock = sync.readLock();
    private final WriteLock writeLock = sync.writeLock();

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final BlockingQueue<StorageStateChange> stateBlockingQueue = new LinkedBlockingQueue<>(4);
    private final Map<String, FeatureFlag> flags = new HashMap<>();
    private final Map<String, Object> flagSetMetadata = new HashMap<>();

    private final QueueSource connector;
    private final boolean throwIfInvalid;

    public FlagStore(final QueueSource connector) {
        this(connector, false);
    }

    public FlagStore(final QueueSource connector, final boolean throwIfInvalid) {
        this.connector = connector;
        this.throwIfInvalid = throwIfInvalid;
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

    /** Retrieve flag for the given key and the flag set metadata. */
    @Override
    public StorageQueryResult getFlag(final String key) {
        readLock.lock();
        FeatureFlag flag;
        Map<String, Object> metadata;
        try {
            flag = flags.get(key);
            metadata = new HashMap<>(flagSetMetadata);
        } finally {
            readLock.unlock();
        }
        return new StorageQueryResult(flag, metadata);
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
                        List<String> changedFlagsKeys = Collections.emptyList();
                        ParsingResult parsingResult = FlagParser.parseString(payload.getFlagData(), throwIfInvalid);
                        Map<String, FeatureFlag> flagMap = parsingResult.getFlags();
                        Map<String, Object> flagSetMetadataMap = parsingResult.getFlagSetMetadata();

                        Structure syncContext = parseSyncContext(payload.getSyncContext());
                        writeLock.lock();
                        try {
                            changedFlagsKeys = getChangedFlagsKeys(flagMap);
                            flags.clear();
                            flags.putAll(flagMap);
                            flagSetMetadata.clear();
                            flagSetMetadata.putAll(flagSetMetadataMap);
                        } finally {
                            writeLock.unlock();
                        }
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

    private List<String> getChangedFlagsKeys(Map<String, FeatureFlag> newFlags) {
        Map<String, FeatureFlag> changedFlags = new HashMap<>();
        Map<String, FeatureFlag> addedFeatureFlags = new HashMap<>();
        Map<String, FeatureFlag> removedFeatureFlags = new HashMap<>();
        Map<String, FeatureFlag> updatedFeatureFlags = new HashMap<>();
        newFlags.forEach((key, value) -> {
            if (!flags.containsKey(key)) {
                addedFeatureFlags.put(key, value);
            } else if (flags.containsKey(key) && !value.equals(flags.get(key))) {
                updatedFeatureFlags.put(key, value);
            }
        });
        flags.forEach((key, value) -> {
            if (!newFlags.containsKey(key)) {
                removedFeatureFlags.put(key, value);
            }
        });
        changedFlags.putAll(addedFeatureFlags);
        changedFlags.putAll(removedFeatureFlags);
        changedFlags.putAll(updatedFeatureFlags);
        return changedFlags.keySet().stream().collect(Collectors.toList());
    }
}
