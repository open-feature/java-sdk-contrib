package dev.openfeature.contrib.providers.gcpparametermanager;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe TTL-based in-memory cache for flag values fetched from GCP Parameter Manager.
 *
 * <p>Entries expire after the configured {@code ttl}. When the cache reaches {@code maxSize},
 * the entry with the earliest insertion time is evicted in O(1) via {@link LinkedHashMap}'s
 * insertion-order iteration and {@code removeEldestEntry}.
 */
class FlagCache {

    private final Map<String, CacheEntry> store;
    private final Duration ttl;

    FlagCache(Duration ttl, int maxSize) {
        this.ttl = ttl;
        this.store = Collections.synchronizedMap(
                new LinkedHashMap<String, CacheEntry>(16, 0.75f, false) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                        return size() > maxSize;
                    }
                });
    }

    /**
     * Returns the cached value for {@code key} if present and not expired.
     *
     * @param key the cache key
     * @return an {@link Optional} containing the cached string, or empty if absent/expired
     */
    Optional<String> get(String key) {
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value);
    }

    /**
     * Stores {@code value} under {@code key}. Eviction of the oldest entry (when the cache is
     * full) is handled automatically by the underlying {@link LinkedHashMap}.
     *
     * @param key   the cache key
     * @param value the value to cache
     */
    void put(String key, String value) {
        store.put(key, new CacheEntry(value, ttl));
    }

    /**
     * Removes the entry for {@code key}, forcing re-fetch on next access.
     *
     * @param key the cache key to invalidate
     */
    void invalidate(String key) {
        store.remove(key);
    }

    /** Removes all entries from the cache. */
    void clear() {
        store.clear();
    }

    private static final class CacheEntry {
        final String value;
        final Instant expiresAt;

        CacheEntry(String value, Duration ttl) {
            this.value = value;
            this.expiresAt = Instant.now().plus(ttl);
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
