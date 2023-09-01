package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FlagParser;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.GrpcStreamConnector;
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
    private final Map<String, FeatureFlag> flags = new ConcurrentHashMap<>();

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final FlagParser parser = new FlagParser();
    private final Connector connector;

    public FlagStore(final FlagdOptions options) {
        this.connector = new GrpcStreamConnector(options);
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
        return flags.get(key);
    }

    private void streamerListener(final Connector connector) throws InterruptedException {
        final BlockingQueue<StreamPayload> streamPayloads = connector.getStream();
        while (!shutdown.get()) {
            StreamPayload take = streamPayloads.take();

            switch (take.getType()) {
                case Data:
                    try {
                        Map<String, FeatureFlag> flagMap = parser.parseString(take.getData());
                        flags.clear();
                        flags.putAll(flagMap);
                    } catch (IOException e) {
                        log.log(Level.WARNING, "Invalid flag sync payload", e);
                    }
                case Error:
                    // todo - event handling
                    break;
            }
        }
    }

}
