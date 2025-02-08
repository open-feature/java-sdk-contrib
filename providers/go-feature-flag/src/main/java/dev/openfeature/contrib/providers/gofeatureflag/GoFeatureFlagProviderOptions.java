package dev.openfeature.contrib.providers.gofeatureflag;

import com.github.benmanes.caffeine.cache.Caffeine;
import dev.openfeature.sdk.ProviderEvaluation;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/** GoFeatureFlagProviderOptions contains the options to initialise the provider. */
@Builder
@Getter
public class GoFeatureFlagProviderOptions {

    /**
     * (mandatory) endpoint contains the DNS of your GO Feature Flag relay proxy. example:
     * https://mydomain.com/gofeatureflagproxy/
     */
    private String endpoint;

    /**
     * (optional) timeout in millisecond we are waiting when calling the go-feature-flag relay proxy
     * API. Default: 10000 ms
     */
    private int timeout;

    /**
     * (optional) maxIdleConnections is the maximum number of connexions in the connexion pool.
     * Default: 1000
     */
    private int maxIdleConnections;

    /**
     * (optional) keepAliveDuration is the time in millisecond we keep the connexion open. Default:
     * 7200000 (2 hours)
     */
    private Long keepAliveDuration;

    /**
     * (optional) If the relay proxy is configured to authenticate the requests, you should provide an
     * API Key to the provider. Please ask the administrator of the relay proxy to provide an API Key.
     * (This feature is available only if you are using GO Feature Flag relay proxy v1.7.0 or above)
     * Default: null
     */
    private String apiKey;

    /**
     * (optional) If cache custom configuration is wanted, you should provide a cache configuration
     * caffeine object. Example:
     *
     * <pre>
     * <code>GoFeatureFlagProviderOptions.builder()
     *   .caffeineConfig(
     *      Caffeine.newBuilder()
     *          .initialCapacity(100)
     *          .maximumSize(100000)
     *          .expireAfterWrite(Duration.ofMillis(5L * 60L * 1000L))
     *          .build()
     *    )
     *   .build();
     * </code>
     * </pre>
     * Default: CACHE_TTL_MS: 5min CACHE_INITIAL_CAPACITY: 100 CACHE_MAXIMUM_SIZE: 100000
     */
    private Caffeine<String, ProviderEvaluation<?>> cacheConfig;

    /** (optional) enable cache value. Default: true */
    private Boolean enableCache;

    /**
     * (optional) interval time we publish statistics collection data to the proxy. The parameter is
     * used only if the cache is enabled, otherwise the collection of the data is done directly when
     * calling the evaluation API. default: 1000 ms
     */
    private Long flushIntervalMs;

    /**
     * (optional) max pending events aggregated before publishing for collection data to the proxy.
     * When an event is added while an events collection is full, the event is omitted. default: 10000
     */
    private Integer maxPendingEvents;

    /**
     * (optional) interval time we poll the proxy to check if the configuration has changed. If the
     * cache is enabled, we will poll the relay-proxy every X milliseconds to check if the
     * configuration has changed. default: 120000
     */
    private Long flagChangePollingIntervalMs;

    /**
     * (optional) disableDataCollection set to true if you don't want to collect the usage of flags
     * retrieved in the cache. default: false
     */
    private boolean disableDataCollection;

    /**
     * (optional) exporterMetadata is the metadata we send to the GO Feature Flag relay proxy when we report the
     * evaluation data usage.
     * ‼️Important: If you are using a GO Feature Flag relay proxy before version v1.41.0, the information of this
     * field will not be added to your feature events.
     */
    private Map<String, Object> exporterMetadata;
}
