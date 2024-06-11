package dev.openfeature.contrib.providers.gofeatureflag;

import com.google.common.cache.CacheBuilder;
import dev.openfeature.sdk.ProviderEvaluation;
import lombok.Builder;
import lombok.Getter;

/**
 * GoFeatureFlagProviderOptions contains the options to initialise the provider.
 */
@Builder
@Getter
public class GoFeatureFlagProviderOptions {

    /**
     * (mandatory) endpoint contains the DNS of your GO Feature Flag relay proxy
     * example: https://mydomain.com/gofeatureflagproxy/
     */
    private String endpoint;

    /**
     * (optional) timeout in millisecond we are waiting when calling the
     * go-feature-flag relay proxy API.
     * Default: 10000 ms
     */
    private int timeout;


    /**
     * (optional) maxIdleConnections is the maximum number of connexions in the connexion pool.
     * Default: 1000
     */
    private int maxIdleConnections;

    /**
     * (optional) keepAliveDuration is the time in millisecond we keep the connexion open.
     * Default: 7200000 (2 hours)
     */
    private Long keepAliveDuration;

    /**
     * (optional) If the relay proxy is configured to authenticate the requests, you should provide
     * an API Key to the provider.
     * Please ask the administrator of the relay proxy to provide an API Key.
     * (This feature is available only if you are using GO Feature Flag relay proxy v1.7.0 or above)
     * Default: null
     */
    private String apiKey;

    /**
     * (optional) If cache custom configuration is wanted, you should provide
     * a cache builder.
     * Default:
     * CACHE_TTL_MS: 5min
     * CACHE_CONCURRENCY_LEVEL: 1
     * CACHE_INITIAL_CAPACITY: 100
     * CACHE_MAXIMUM_SIZE: 100000
     */
    private CacheBuilder<String, ProviderEvaluation<?>> cacheBuilder;

    /**
     * (optional) enable cache value.
     * Default: true
     */
    private Boolean enableCache;

    /**
     * (optional) interval time we publish statistics collection data to the proxy.
     * The parameter is used only if the cache is enabled, otherwise the collection of the data is done directly
     * when calling the evaluation API.
     * default: 1000 ms
     */
    private Long flushIntervalMs;

    /**
     * (optional) max pending events aggregated before publishing for collection data to the proxy.
     * When an event is added while an events collection is full, the event is omitted.
     * default: 10000
     */
    private Integer maxPendingEvents;

    /**
     * (optional) interval time we poll the proxy to check if the configuration has changed.
     * If the cache is enabled, we will poll the relay-proxy every X milliseconds
     * to check if the configuration has changed.
     * default: 120000
     */
    private Long flagChangePollingIntervalMs;

    /**
     * (optional) disableDataCollection set to true if you don't want to collect the usage of
     * flags retrieved in the cache.
     * default: false
     */
    private boolean disableDataCollection;
}
