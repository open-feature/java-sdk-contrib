package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FlagParser;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayload;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Feature flag storage.
 */
@Slf4j
@SuppressFBWarnings(value = {"EI_EXPOSE_REP"},
        justification = "Feature flag comes as a Json configuration, hence they must be exposed")
public class FlagStore implements Storage {
    private final ReentrantReadWriteLock sync = new ReentrantReadWriteLock();
    private final ReadLock readLock = sync.readLock();
    private final WriteLock writeLock = sync.writeLock();

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final BlockingQueue<StorageStateDTO> stateBlockingQueue = new LinkedBlockingQueue<>(1);
    private final Map<String, FeatureFlag> flags = new HashMap<>();

    private final Connector connector;
    private final boolean throwIfInvalid;

    public FlagStore(final Connector connector) {
        this(connector, false);
    }

    public FlagStore(final Connector connector, final boolean throwIfInvalid) {
        this.connector = connector;
        this.throwIfInvalid = throwIfInvalid;
    }

    /**
     * Initialize storage layer.
     */
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
    public void shutdown() throws InterruptedException {
        if (shutdown.getAndSet(true)) {
            return;
        }

        connector.shutdown();
    }

    /**
     * Retrieve flag for the given key.
     */
    public FeatureFlag getFlag(final String key) {
        readLock.lock();
        try {
            return flags.get(key);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Retrieve blocking queue to check storage status.
     */
    public BlockingQueue<StorageStateDTO> getStateQueue() {
        return stateBlockingQueue;
    }

    private void streamerListener(final Connector connector) throws InterruptedException {
        final BlockingQueue<StreamPayload> streamPayloads = connector.getStream();

        while (!shutdown.get()) {
            final StreamPayload take = streamPayloads.take();
            switch (take.getType()) {
                case DATA:
                    try {
                        List<String> changedFlagsKeys;
                        Map<String, FeatureFlag> flagMap = FlagParser.parseString(take.getData(), throwIfInvalid);
                        writeLock.lock();
                        try {
                            changedFlagsKeys = getChangedFlagsKeys(flagMap);
                            flags.clear();
                            flags.putAll(flagMap);
                        } finally {
                            writeLock.unlock();
                        }
                        if (!stateBlockingQueue.offer(new StorageStateDTO(StorageState.OK, changedFlagsKeys))) {
                            log.warn("Failed to convey OK satus, queue is full");
                        }
                    } catch (Throwable e) {
                        // catch all exceptions and avoid stream listener interruptions
                        log.warn("Invalid flag sync payload from connector", e);
                        if (!stateBlockingQueue.offer(new StorageStateDTO(StorageState.STALE))) {
                            log.warn("Failed to convey STALE satus, queue is full");
                        }
                    }
                    break;
                case ERROR:
                    if (!stateBlockingQueue.offer(new StorageStateDTO(StorageState.ERROR))) {
                        log.warn("Failed to convey ERROR satus, queue is full");
                    }
                    break;
                default:
                    log.info(String.format("Payload with unknown type: %s", take.getType()));
            }
        }

        log.info("Shutting down store stream listener");
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
        flags.forEach((key,value) -> {
            if(!newFlags.containsKey(key)) {
                removedFeatureFlags.put(key, value);
            }
        });
        changedFlags.putAll(addedFeatureFlags);
        changedFlags.putAll(removedFeatureFlags);
        changedFlags.putAll(updatedFeatureFlags);
        return changedFlags.keySet().stream().collect(Collectors.toList());
    }

}
