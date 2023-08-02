package dev.openfeature.contrib.providers.gofeatureflag.exception;

public class NotPresentInCache extends GoFeatureFlagException {
    public NotPresentInCache(String cacheKey){
        super("No item found for key: "+ cacheKey);
    }
}
