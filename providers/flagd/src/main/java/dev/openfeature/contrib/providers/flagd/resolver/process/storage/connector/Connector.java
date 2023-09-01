package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector;

import java.util.function.Consumer;

public interface Connector {
    void init(Consumer<String> callback);
    void shutdown();
}
