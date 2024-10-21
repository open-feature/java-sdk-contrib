package dev.openfeature.contrib.providers.multiprovider;

import dev.openfeature.sdk.FeatureProvider;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.openfeature.contrib.providers.multiprovider.MultiProvider.buildProviders;

/**
 * Base strategy.
 */
@Slf4j
public abstract class BaseStrategy implements Strategy {

    @Getter(AccessLevel.PROTECTED)
    private final Map<String, FeatureProvider> providers;

    public BaseStrategy(List<FeatureProvider> providers) {
        this.providers = buildProviders(providers);
    }

}
