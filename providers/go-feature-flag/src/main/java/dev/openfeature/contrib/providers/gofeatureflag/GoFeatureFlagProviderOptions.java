package dev.openfeature.contrib.providers.gofeatureflag;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheBuilderSpec;
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
     *  (optional) If the relay proxy is configured to authenticate the requests, you should provide
     *  an API Key to the provider.
     *  Please ask the administrator of the relay proxy to provide an API Key.
     *  (This feature is available only if you are using GO Feature Flag relay proxy v1.7.0 or above)
     *  Default: null
     */
    private String apiKey;

    /**
     *  (optional) If cache custom configuration is wanted, you should provide
     *  a cache builder.
     *  Default: null
     */
    private CacheBuilder cacheBuilder;

    /**
     * (optional) enable cache value
     * Default: true
     */
    private Boolean enableCache;

    /**
     * (optional) interval time we publish statistics collection data to the proxy.
     * 	The parameter is used only if the cache is enabled, otherwise the collection of the data is done directly
     * 	when calling the evaluation API.
     * 	default: 1 minute
     */
    private Long flushIntervalMinues;
}
