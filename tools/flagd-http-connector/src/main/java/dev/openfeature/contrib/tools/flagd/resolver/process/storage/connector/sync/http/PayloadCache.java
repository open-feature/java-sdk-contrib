package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

/**
 * Interface for a simple payload cache.
 */
public interface PayloadCache {

    String get(String key);

    void put(String key, String payload);

    /**
     * Put a payload into the cache with a time-to-live (TTL) value.
     * Must implement if HttpConnectorOptions.usePollingCache is true.
     *
     * @param key cache key
     * @param payload payload to cache
     * @param ttlSeconds time-to-live in seconds
     */
    default void put(String key, String payload, int ttlSeconds) {
        throw new UnsupportedOperationException("put with ttl not supported");
    }
}
