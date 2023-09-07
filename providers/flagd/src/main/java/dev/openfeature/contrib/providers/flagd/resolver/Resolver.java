package dev.openfeature.contrib.providers.flagd.resolver;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;

/**
 * A generic flag resolving contract for flagd.
 */
public interface Resolver {
    void init() throws Exception;

    void shutdown() throws Exception;

    ProviderEvaluation<Boolean> booleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx);

    ProviderEvaluation<String> stringEvaluation(String key, String defaultValue, EvaluationContext ctx);

    ProviderEvaluation<Double> doubleEvaluation(String key, Double defaultValue, EvaluationContext ctx);

    ProviderEvaluation<Integer> integerEvaluation(String key, Integer defaultValue, EvaluationContext ctx);

    ProviderEvaluation<Value> objectEvaluation(String key, Value defaultValue, EvaluationContext ctx);
}
