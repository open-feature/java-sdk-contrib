package dev.openfeature.contrib.providers.gofeatureflag.exception;

/**
 * Exception thrown when the cache does not contain the key.
 */
public class NotPresentInCache extends GoFeatureFlagException {
    public NotPresentInCache(String cacheKey) {
        super("No item found for key: " + cacheKey);
    }
}
