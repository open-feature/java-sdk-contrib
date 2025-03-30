package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * A wrapper class for managing a payload cache with a specified update interval.
 * This class ensures that the cache is only updated if the specified time interval
 * has passed since the last update. It logs debug messages when updates are skipped
 * and error messages if the update process fails.
 * Not thread-safe.
 *
 * <p>Usage involves creating an instance with {@link PayloadCacheOptions} to set
 * the update interval, and then using {@link #updatePayloadIfNeeded(String)} to
 * conditionally update the cache and {@link #get()} to retrieve the cached payload.</p>
 */
@Slf4j
public class PayloadCacheWrapper {
    private long lastUpdateTimeMs;
    private long updateIntervalMs;
    private PayloadCache payloadCache;

    @Builder
    public PayloadCacheWrapper(PayloadCache payloadCache, PayloadCacheOptions payloadCacheOptions) {
        if (payloadCacheOptions.getUpdateIntervalSeconds() < 500) {
            throw new IllegalArgumentException("pollIntervalSeconds must be larger than 500");
        }
        this.updateIntervalMs = payloadCacheOptions.getUpdateIntervalSeconds() * 1000;
        this.payloadCache = payloadCache;
    }

    public void updatePayloadIfNeeded(String payload) {
        if ((System.currentTimeMillis() - lastUpdateTimeMs) < updateIntervalMs) {
            log.debug("not updating payload, updateIntervalMs not reached");
            return;
        }

        try {
            log.debug("updating payload");
            payloadCache.put(payload);
            lastUpdateTimeMs = System.currentTimeMillis();
        } catch (Exception e) {
            log.error("failed updating cache", e);
        }
    }

    public String get() {
        try {
            return payloadCache.get();
        } catch (Exception e) {
            log.error("failed getting from cache", e);
            return null;
        }
    }
}
