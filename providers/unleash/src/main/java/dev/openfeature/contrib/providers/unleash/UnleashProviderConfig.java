package dev.openfeature.contrib.providers.unleash;

import io.getunleash.util.UnleashConfig;
import lombok.Builder;
import lombok.Getter;


/**
 * Options for initializing Unleash provider.
 */
@Getter
@Builder
public class UnleashProviderConfig {
    private UnleashConfig.Builder unleashConfigBuilder;
}
