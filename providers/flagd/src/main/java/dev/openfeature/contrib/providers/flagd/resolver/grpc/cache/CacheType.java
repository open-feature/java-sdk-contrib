package dev.openfeature.contrib.providers.flagd.resolver.grpc.cache;

import static dev.openfeature.contrib.providers.flagd.Config.LRU_CACHE;

/**
 * Defines which type of cache to use.
 */
public enum CacheType {
    DISABLED("disabled"),
    LRU(LRU_CACHE);

    private final String type;

    CacheType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
