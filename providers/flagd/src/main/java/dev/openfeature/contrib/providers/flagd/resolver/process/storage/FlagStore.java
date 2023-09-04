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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

@Log
public class FlagStore implements Storage {
    private final Object sync = new Object();
    private final Map<String, FeatureFlag> flags = new ConcurrentHashMap<>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final Connector connector;

    public FlagStore(final Connector connector) {
        this.connector = connector;
    }

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

    public void shutdown() {
        shutdown.set(true);
        connector.shutdown();
    }

    public FeatureFlag getFLag(final String key) {
        synchronized (sync) {
            return flags.get(key);
        }
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
                    } catch (IOException e) {
                        log.log(Level.WARNING, "Invalid flag sync payload from connector", e);
                    }
                case Error:
                    // todo - event handling
                    break;
            }
        }
    }

}
