package dev.openfeature.contrib.providers.flagd;

import java.util.Collections;
import java.util.Map;

import org.apache.commons.collections4.map.LRUMap;

import dev.openfeature.sdk.ProviderEvaluation;

import static dev.openfeature.contrib.providers.flagd.Config.LRU_CACHE;

/**
 * Exposes caching mechanism for flag evaluations.
 */
public class FlagdCache {
    private Map<String,ProviderEvaluation<? extends Object>> store;
    private Boolean enabled = false;

    static final String DISABLED = "disabled";

    FlagdCache(String cache, int maxCacheSize) {
        if (cache == null) {
            return;
        }

        switch (cache) {
            case DISABLED:
                return;
            case LRU_CACHE:
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
