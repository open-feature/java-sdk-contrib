package dev.openfeature.contrib.providers.flagd.resolver.rpc.cache;

import static dev.openfeature.contrib.providers.flagd.resolver.rpc.cache.CacheType.DISABLED;
import static dev.openfeature.contrib.providers.flagd.resolver.rpc.cache.CacheType.LRU;

import dev.openfeature.sdk.ProviderEvaluation;
import java.util.Collections;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;

/** Exposes caching mechanism for flag evaluations. */
@Slf4j
public class Cache {
    private Map<String, ProviderEvaluation<?>> store;

    @Getter
    private final Boolean enabled;

    /**
     * Initialize the cache.
     *
     * @param forType type of the cache.
     * @param maxCacheSize max amount of element to keep.
     */
    public Cache(final String forType, int maxCacheSize) {
        if (DISABLED.getValue().equals(forType)) {
            enabled = false;
        } else if (LRU.getValue().equals(forType)) {
            enabled = true;
            this.store = Collections.synchronizedMap(new LRUMap<>(maxCacheSize));
        } else {
            enabled = false;
            log.warn(String.format("Unsupported cache type %s, continuing without cache", forType));
        }
    }

    public void put(String key, ProviderEvaluation<?> value) {
        this.store.put(key, value);
    }

    public ProviderEvaluation<?> get(String key) {
        return this.store.get(key);
    }

    public void remove(String key) {
        this.store.remove(key);
    }

    public void clear() {
        this.store.clear();
    }
}
