package dev.openfeature.contrib.providers.flagd.resolver.grpc.cache;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;

/**
 * Factory to create a Cache.
 */
public class CacheFactory {
    /**
     * Factory method to initialize the cache strategy.
     * @param options Options.
     * @return the Cache based on the provided options.
     */
    public static Cache getCache(FlagdOptions options) {
        CacheType cacheType;
        try {
            cacheType = CacheType.valueOf(options.getCacheType());
        } catch (IllegalArgumentException e) {
            cacheType = CacheType.DISABLED;
        }
        return new Cache(cacheType, options.getMaxCacheSize());
    }
}
