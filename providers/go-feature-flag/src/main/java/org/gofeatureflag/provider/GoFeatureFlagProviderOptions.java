package org.gofeatureflag.provider;

import lombok.Builder;
import lombok.Getter;

@Builder
public class GoFeatureFlagProviderOptions {
    /**
     * timeout in millisecond we are waiting when calling the
     * go-feature-flag relay proxy API.
     */
    @Getter
    private int timeout;

    @Getter
    private String endpoint;
}
