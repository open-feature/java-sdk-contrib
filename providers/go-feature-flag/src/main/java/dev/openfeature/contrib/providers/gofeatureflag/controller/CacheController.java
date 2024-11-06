package dev.openfeature.contrib.providers.gofeatureflag.controller;

import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.bean.BeanUtils;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import lombok.Builder;

/**
 * CacheController is a controller to manage the cache of the provider.
 */
public class CacheController {
    public static final long DEFAULT_CACHE_TTL_MS = 5L * 60L * 1000L;
    public static final int DEFAULT_CACHE_INITIAL_CAPACITY = 100;
    public static final int DEFAULT_CACHE_MAXIMUM_SIZE = 100000;
    private final Cache<String, ProviderEvaluation<?>> cache;

    @Builder
    public CacheController(GoFeatureFlagProviderOptions options) {
        this.cache = options.getCacheConfig() != null ? options.getCacheConfig().build() : buildDefaultCache();
    }

    private Cache<String, ProviderEvaluation<?>> buildDefaultCache() {
        return Caffeine.newBuilder()
                .initialCapacity(DEFAULT_CACHE_INITIAL_CAPACITY)
                .maximumSize(DEFAULT_CACHE_MAXIMUM_SIZE)
                .expireAfterWrite(Duration.ofMillis(DEFAULT_CACHE_TTL_MS))
                .build();
    }

    public void put(final String key, final EvaluationContext evaluationContext,
                    final ProviderEvaluation<?> providerEvaluation) throws JsonProcessingException {
        this.cache.put(buildCacheKey(key, evaluationContext), providerEvaluation);
    }

    public ProviderEvaluation<?> getIfPresent(final String key, final EvaluationContext evaluationContext)
            throws JsonProcessingException {
        return this.cache.getIfPresent(buildCacheKey(key, evaluationContext));
    }

    public void invalidateAll() {
        this.cache.invalidateAll();
    }

    private String buildCacheKey(String key, EvaluationContext evaluationContext) throws JsonProcessingException {
        String originalKey = key + "," + BeanUtils.buildKey(evaluationContext);
        int hash = originalKey.hashCode();
        return String.valueOf(hash);
    }
}
