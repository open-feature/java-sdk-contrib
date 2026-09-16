package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;

/**
 * IEvaluator is an interface that represents the evaluation of a feature flag.
 * It can have multiple implementations: REMOTE or IN-PROCESS.
 */
public interface IEvaluator {
    void initialize(final EvaluationContext ctx, final String domain) throws Exception;
    void initialize(final EvaluationContext ctx) throws Exception;
    void shutdown();
    ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx);
    ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx);
    ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx);
    ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx);
    ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx);
    boolean isFlagTrackable(String flagKey);
}
