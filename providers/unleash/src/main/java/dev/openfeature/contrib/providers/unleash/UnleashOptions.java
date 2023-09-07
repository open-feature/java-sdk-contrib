package dev.openfeature.contrib.providers.unleash;

import io.getunleash.strategy.Strategy;
import io.getunleash.util.UnleashConfig;
import lombok.Builder;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Options for initializing Unleash provider.
 */
@Getter
@Builder
public class UnleashOptions {
    private UnleashConfig.Builder unleashConfigBuilder;
    @Nullable private Map<String, Strategy> strategyMap;
    @Nullable private boolean failOnMultipleInstantiations;
}
