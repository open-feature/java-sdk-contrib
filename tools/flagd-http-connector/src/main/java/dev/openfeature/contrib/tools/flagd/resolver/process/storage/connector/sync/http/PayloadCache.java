package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

/**
 * Interface for a simple payload cache.
 */
public interface PayloadCache {

    void put(String payload);

    String get();
}
