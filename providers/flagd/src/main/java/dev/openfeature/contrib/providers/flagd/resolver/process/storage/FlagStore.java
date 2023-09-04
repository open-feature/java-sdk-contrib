package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FlagParser;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayload;
import lombok.extern.java.Log;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Feature flag storage.
 */
@Log
public class FlagStore implements Storage {
    private final Object sync = new Object();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final BlockingQueue<StorageState> stateBlockingQueue = new LinkedBlockingQueue<>(1);
    private final Map<String, FeatureFlag> flags = new ConcurrentHashMap<>();

    private final Connector connector;

    public FlagStore(final Connector connector) {
        this.connector = connector;
    }

    /**
     * Initialize storage layer.
     */
    public void init() {
        connector.init();
        Thread streamer = new Thread(() -> {
            try {
                streamerListener(connector);
            } catch (InterruptedException e) {
                log.log(Level.WARNING, "connection listener failed", e);
            }
        });
        streamer.setDaemon(true);
        streamer.start();
    }

    /**
     * Shutdown storage layer.
     */
    public void shutdown() {
        shutdown.set(true);
        connector.shutdown();
    }

    /**
     * Retrieve flag for the given key.
     */
    public FeatureFlag getFLag(final String key) {
        synchronized (sync) {
            return flags.get(key);
        }
    }

    /**
     * Retrieve blocking queue to check storage status.
     */
    public BlockingQueue<StorageState> getStateQueue() {
        return stateBlockingQueue;
    }

    private void streamerListener(final Connector connector) throws InterruptedException {
        final BlockingQueue<StreamPayload> streamPayloads = connector.getStream();

        while (!shutdown.get()) {
            final StreamPayload take = streamPayloads.take();
            switch (take.getType()) {
                case Data:
                    try {
                        Map<String, FeatureFlag> flagMap = FlagParser.parseString(take.getData());
                        synchronized (sync) {
                            flags.clear();
                            flags.putAll(flagMap);
                        }
                        stateBlockingQueue.offer(StorageState.OK);
                    } catch (IOException e) {
                        log.log(Level.WARNING, "Invalid flag sync payload from connector", e);
                        stateBlockingQueue.offer(StorageState.STALE);
                    }
                    break;
                case Error:
                    stateBlockingQueue.offer(StorageState.ERROR);
                    break;
                default:
                    log.log(Level.INFO, String.format("Payload with unknown type: %s", take.getType()));
            }
        }
    }

}
