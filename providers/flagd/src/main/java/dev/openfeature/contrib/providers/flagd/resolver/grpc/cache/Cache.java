package dev.openfeature.contrib.providers.flagd.resolver.grpc.cache;

import dev.openfeature.sdk.ProviderEvaluation;
import org.apache.commons.collections4.map.LRUMap;

import java.util.Collections;
import java.util.Map;

/**
 * Exposes caching mechanism for flag evaluations.
 */
public class Cache {
    private Map<String,ProviderEvaluation<? extends Object>> store;
    private Boolean enabled = false;

    /**
     * Initialize the cache.
     * @param type of cache.
     * @param maxCacheSize max amount of element to keep.
     */
    public Cache(CacheType type, int maxCacheSize) {
        if (type == null) {
            return;
        }

        switch (type) {
            case DISABLED:
                return;
            case LRU:
            default:
                this.store = Collections.synchronizedMap(new LRUMap<>(maxCacheSize));
        }

        this.enabled = true;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void put(String key, ProviderEvaluation<? extends Object> value) {
        this.store.put(key, value);
    }

    public ProviderEvaluation<? extends Object> get(String key) {
        return this.store.get(key);
    }

    public void remove(String key) {
        this.store.remove(key);
    }

    public void clear() {
        this.store.clear();
    }
}
