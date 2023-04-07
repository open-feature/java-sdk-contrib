package dev.openfeature.contrib.providers.gofeatureflag;

import lombok.Builder;
import lombok.Getter;

/**
 * GoFeatureFlagProviderOptions contains the options to initialise the provider.
 */
@Builder
public class GoFeatureFlagProviderOptions {

    /**
     * (mandatory) endpoint contains the DNS of your GO Feature Flag relay proxy
     * example: https://mydomain.com/gofeatureflagproxy/
     */
    @Getter
    private String endpoint;

    /**
     * (optional) timeout in millisecond we are waiting when calling the
     * go-feature-flag relay proxy API.
     * Default: 10000 ms
     */
    @Getter
    private int timeout;


    /**
     * (optional) maxIdleConnections is the maximum number of connexions in the connexion pool.
     * Default: 1000
     */
    @Getter
    private int maxIdleConnections;

    /**
     * (optional) keepAliveDuration is the time in millisecond we keep the connexion open.
     * Default: 7200000 (2 hours)
     */
    @Getter
    private Long keepAliveDuration;

    /**
     *  (optional) If the relay proxy is configured to authenticate the requests, you should provide
     *  an API Key to the provider.
     *
     *  Please ask the administrator of the relay proxy to provide an API Key.
     *  (This feature is available only if you are using GO Feature Flag relay proxy v1.7.0 or above)
     *  Default: null
     */
    @Getter
    private String apiKey;
}
