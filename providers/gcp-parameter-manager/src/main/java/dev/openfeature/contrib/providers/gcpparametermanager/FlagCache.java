package dev.openfeature.contrib.providers.gcpparametermanager;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe TTL-based in-memory cache for flag values fetched from GCP Parameter Manager.
 *
 * <p>Entries expire after the configured {@code ttl}. When the cache reaches {@code maxSize},
 * the entry with the earliest expiry is evicted before inserting the new entry.
 */
class FlagCache {

    private final Map<String, CacheEntry> store = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxSize;

    FlagCache(Duration ttl, int maxSize) {
        this.ttl = ttl;
        this.maxSize = maxSize;
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
     * Stores {@code value} under {@code key}, evicting the oldest entry if the cache is full.
     *
     * @param key   the cache key
     * @param value the value to cache
     */
    void put(String key, String value) {
        if (store.size() >= maxSize && !store.containsKey(key)) {
            evictOldest();
        }
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

    private void evictOldest() {
        store.entrySet().stream()
                .min(Comparator.comparing(e -> e.getValue().expiresAt))
                .map(Map.Entry::getKey)
                .ifPresent(store::remove);
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
