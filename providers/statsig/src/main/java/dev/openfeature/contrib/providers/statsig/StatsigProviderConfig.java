package dev.openfeature.contrib.providers.statsig;

import com.statsig.sdk.StatsigOptions;
import lombok.Builder;
import lombok.Getter;


/**
 * Configuration for initializing statsig provider.
 */
@Getter
@Builder
public class StatsigProviderConfig {

    private StatsigOptions options;

    // Only holding temporary for initialization
    private String sdkKey;

    public void postInit() {
        sdkKey = null; // for security, not holding key in memory for long-term
    }
}
