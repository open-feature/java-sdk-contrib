package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FlagParser;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.GrpcStreamConnector;
import lombok.extern.java.Log;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Log
public class FlagStore implements Storage {
    private final Map<String, FeatureFlag> flags = new ConcurrentHashMap<>();
    private final FlagParser parser = new FlagParser();
    private final Connector connector;

    public FlagStore(final FlagdOptions options) {
        this.connector = new GrpcStreamConnector(options);
    }

    public void init() {
        connector.init(this::setFlags);
    }

    public void shutdown() {
        connector.shutdown();
    }

    public void setFlags(final String configuration) {
        try {
            parser.parseString(configuration);
        } catch (IOException e) {
            log.log(Level.WARNING, "flag configuration parsing failed", e);
        }
    }

    public FeatureFlag getFLag(final String key) {
        return flags.get(key);
    }

}
