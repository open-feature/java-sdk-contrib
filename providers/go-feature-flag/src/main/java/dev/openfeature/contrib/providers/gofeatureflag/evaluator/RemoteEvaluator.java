package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import dev.openfeature.contrib.providers.gofeatureflag.api.GoFeatureFlagApi;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.sdk.EvaluationContext;
import lombok.extern.slf4j.Slf4j;

/**
 * RemoteEvaluator is an implementation of the IEvaluator interface.
 * It is used to evaluate the feature flags using the GO Feature Flag API.
 */
@Slf4j
public class RemoteEvaluator implements IEvaluator {
    /** API to contact GO Feature Flag. */
    public final GoFeatureFlagApi api;

    /**
     * Constructor of the evaluator.
     *
     * @param api - api service to evaluate the flags
     */
    public RemoteEvaluator(GoFeatureFlagApi api) {
        this.api = api;
    }

    @Override
    public GoFeatureFlagResponse evaluate(String key, Object defaultValue, EvaluationContext evaluationContext) {
        return this.api.evaluateFlag(key, evaluationContext);
    }

    @Override
    public boolean isFlagTrackable(String flagKey) {
        return true;
    }

    @Override
    public void init() {
        // do nothing
    }

    @Override
    public void destroy() {
        // do nothing
    }
}
