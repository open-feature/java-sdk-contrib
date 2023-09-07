package dev.openfeature.contrib.providers.unleash;

import io.getunleash.UnleashContextProvider;
import io.getunleash.event.EventDispatcher;
import io.getunleash.metric.UnleashMetricService;
import io.getunleash.repository.IFeatureRepository;
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
    @Nullable private IFeatureRepository featureRepository;
    @Nullable private Map<String, Strategy> strategyMap;
    @Nullable private UnleashContextProvider contextProvider;
    @Nullable private EventDispatcher eventDispatcher;
    @Nullable private UnleashMetricService metricService;
    @Nullable private boolean failOnMultipleInstantiations;
}
