package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;

import java.util.Map;
import org.apache.commons.collections4.map.LRUMap;
import java.util.Collections;

/**
 * Exposes caching mechanism for flag evaluations.
 */
public class FlagdCache {
    private Map<String,ProviderEvaluation<Value>> store;
    private Boolean enabled;

    static final String LRU_CACHE = "lru";
    static final String DISABLED = "disabled";

    FlagdCache(String cache, int maxCacheSize) {
        switch (cache) {
            case DISABLED:
                return;
            case LRU_CACHE:
            default:
                this.store = Collections.synchronizedMap(new LRUMap<String, ProviderEvaluation<Value>>(maxCacheSize));
        }

        this.enabled = true;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void put(String key, ProviderEvaluation<Value> value) {
        this.store.put(key, value);
    }

    public ProviderEvaluation<Value> get(String key) {
        return this.store.get(key);
    }

    public void remove(String key) {
        this.store.remove(key);
    }

    public void clear() {
        this.store.clear();
    }
}
