package dev.openfeature.contrib.providers.multiprovider;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ProviderEvaluation;

import java.util.function.Function;

/**
 * strategy.
 */
public interface Strategy {
    <T> ProviderEvaluation<T> evaluate(String key, T defaultValue, EvaluationContext ctx,
           Function<FeatureProvider, ProviderEvaluation<T>> providerFunction);
}
