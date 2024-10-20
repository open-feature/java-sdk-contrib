package dev.openfeature.contrib.providers.multiprovider;

import dev.openfeature.sdk.FeatureProvider;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@AllArgsConstructor
public abstract class BaseStrategy implements Strategy {

    @Getter(AccessLevel.PROTECTED)
    private final Map<String, FeatureProvider> providers;

}
