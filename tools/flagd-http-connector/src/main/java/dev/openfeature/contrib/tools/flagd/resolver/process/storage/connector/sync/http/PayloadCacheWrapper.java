package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

import lombok.Builder;
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
        if (payloadCacheOptions.getUpdateIntervalSeconds() < 1) {
            throw new IllegalArgumentException("pollIntervalSeconds must be larger than 0");
        }
        this.updateIntervalMs = payloadCacheOptions.getUpdateIntervalSeconds() * 1000L;
        this.payloadCache = payloadCache;
    }

    public void updatePayloadIfNeeded(String payload) {
        if ((getCurrentTimeMillis() - lastUpdateTimeMs) < updateIntervalMs) {
            log.debug("not updating payload, updateIntervalMs not reached");
            return;
        }

        try {
            log.debug("updating payload");
            payloadCache.put(payload);
            lastUpdateTimeMs = getCurrentTimeMillis();
        } catch (Exception e) {
            log.error("failed updating cache", e);
        }
    }

    protected long getCurrentTimeMillis() {
        return System.currentTimeMillis();
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
