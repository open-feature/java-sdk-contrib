package org.gofeatureflag.provider;

import lombok.Builder;
import lombok.Getter;

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
}
