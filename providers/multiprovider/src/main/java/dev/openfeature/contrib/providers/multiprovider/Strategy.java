package dev.openfeature.contrib.providers.multiprovider;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ProviderEvaluation;
import java.util.Map;
import java.util.function.Function;

/**
 * Strategy for evaluating flags across multiple providers.
 *
 * @deprecated Use the strategy classes in {@code dev.openfeature.sdk.multiprovider}
 *     from the core Java SDK instead.
 */
@Deprecated
public interface Strategy {
    <T> ProviderEvaluation<T> evaluate(
            Map<String, FeatureProvider> providers,
            String key,
            T defaultValue,
            EvaluationContext ctx,
            Function<FeatureProvider, ProviderEvaluation<T>> providerFunction);
}
