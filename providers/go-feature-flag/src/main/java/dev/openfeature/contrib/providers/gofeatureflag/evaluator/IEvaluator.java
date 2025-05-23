package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.sdk.EvaluationContext;

/**
 * IEvaluator is an interface that represents the evaluation of a feature flag.
 * It can have multiple implementations: EDGE or IN-PROCESS.
 */
public interface IEvaluator {
    /**
     * Initialize the evaluator.
     */
    void init();

    /**
     * Destroy the evaluator.
     */
    void destroy();

    /**
     * Evaluate the flag.
     *
     * @param key               - name of the flag
     * @param defaultValue      - default value
     * @param evaluationContext - evaluation context
     * @return the evaluation response
     */
    GoFeatureFlagResponse evaluate(String key, Object defaultValue, EvaluationContext evaluationContext);

    /**
     * Check if the flag is trackable or not.
     *
     * @param flagKey - name of the flag
     * @return true if the flag is trackable, false otherwise
     */
    boolean isFlagTrackable(String flagKey);
}
