package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP2", "CT_CONSTRUCTOR_THROW"},
    justification = "builder validations"
)
@Slf4j
public class FailSafeCache {
    public static final String FAILSAFE_PAYLOAD_CACHE_KEY = FailSafeCache.class.getSimpleName()
        + ".failsafe-payload";
    private long lastUpdateTimeMs;
    private long updateIntervalMs;
    private PayloadCache payloadCache;

    /**
     * Constructor for FailSafeCache.
     *
     * @param payloadCache the payload cache to be used
     * @param payloadCacheOptions the options for configuring the cache
     */
    @Builder
    public FailSafeCache(PayloadCache payloadCache, PayloadCacheOptions payloadCacheOptions) {
        validate(payloadCacheOptions);
        this.updateIntervalMs = payloadCacheOptions.getUpdateIntervalSeconds() * 1000L;
        this.payloadCache = payloadCache;
    }

    private static void validate(PayloadCacheOptions payloadCacheOptions) {
        if (payloadCacheOptions.getUpdateIntervalSeconds() < 1) {
            throw new IllegalArgumentException("pollIntervalSeconds must be larger than 0");
        }
    }

    /**
     * Updates the payload in the cache if the specified update interval has passed.
     *
     * @param payload the payload to be cached
     */
    public void updatePayloadIfNeeded(String payload) {
        if ((getCurrentTimeMillis() - lastUpdateTimeMs) < updateIntervalMs) {
            log.debug("not updating payload, updateIntervalMs not reached");
            return;
        }

        try {
            log.debug("updating payload");
            payloadCache.put(FAILSAFE_PAYLOAD_CACHE_KEY, payload);
            lastUpdateTimeMs = getCurrentTimeMillis();
        } catch (Exception e) {
            log.error("failed updating cache", e);
        }
    }

    protected long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Retrieves the cached payload.
     *
     * @return the cached payload
     */
    public String get() {
        try {
            return payloadCache.get(FAILSAFE_PAYLOAD_CACHE_KEY);
        } catch (Exception e) {
            log.error("failed getting from cache", e);
            return null;
        }
    }
}
