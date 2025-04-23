package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.sdk.EvaluationContext;

public interface IEvaluator {
    void init();

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
