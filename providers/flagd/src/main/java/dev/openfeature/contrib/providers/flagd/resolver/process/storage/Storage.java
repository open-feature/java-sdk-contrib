package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;

import java.util.concurrent.BlockingQueue;

/**
 * Storage abstraction for resolver.
 */
public interface Storage {
    void init() throws Exception;

    void shutdown() throws InterruptedException;

    FeatureFlag getFlag(final String key);

    BlockingQueue<StorageState> getStateQueue();
}
