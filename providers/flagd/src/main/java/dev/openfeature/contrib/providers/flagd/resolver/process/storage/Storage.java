package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import java.util.concurrent.BlockingQueue;

/** Storage abstraction for resolver. */
public interface Storage {
    void init() throws Exception;

    void shutdown() throws InterruptedException;

    StorageQueryResult getFlag(final String key);

    BlockingQueue<StorageStateChange> getStateQueue();
}
