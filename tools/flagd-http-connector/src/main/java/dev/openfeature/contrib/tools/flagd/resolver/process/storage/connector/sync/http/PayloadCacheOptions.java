package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents configuration options for caching payloads.
 *
 * <p>This class provides options to configure the caching behavior,
 * specifically the interval at which the cache should be updated.
 * </p>
 *
 * <p>The default update interval is set to 30 minutes.
 * Change it typically to a value according to cache ttl and tradeoff with not updating it too much for
 * corner cases.
 * </p>
 */
@Builder
@Getter
public class PayloadCacheOptions {

    @Builder.Default
    private int updateIntervalSeconds = 60 * 30; // 30 minutes
}
