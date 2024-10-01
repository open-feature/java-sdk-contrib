package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector;

import java.util.concurrent.BlockingQueue;

/**
 * Contract of the in-process storage connector. Connectors are responsible to stream flag configurations in
 * {@link QueuePayload} format.
 */
public interface Connector {
    void init() throws Exception;

    BlockingQueue<QueuePayload> getStream();

    void shutdown() throws InterruptedException;
}
