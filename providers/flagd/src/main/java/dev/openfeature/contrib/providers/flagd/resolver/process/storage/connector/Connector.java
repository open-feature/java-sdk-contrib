package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector;

import java.util.concurrent.BlockingQueue;

public interface Connector {
    void init();

    BlockingQueue<StreamPayload> getStream();

    void shutdown();
}
