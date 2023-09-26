package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector;

import java.util.concurrent.BlockingQueue;

/**
 * Contract of the in-process storage connector. Connectors are responsible to stream flag configurations in
 * {@link StreamPayload} format.
 */
public interface Connector {
    void init();

    BlockingQueue<StreamPayload> getStream();

    void shutdown() throws InterruptedException;
}
