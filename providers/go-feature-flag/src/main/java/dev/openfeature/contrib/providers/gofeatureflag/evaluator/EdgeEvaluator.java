package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import dev.openfeature.contrib.providers.gofeatureflag.api.GoFeatureFlagApi;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.sdk.EvaluationContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EdgeEvaluator implements IEvaluator {
    /** API to contact GO Feature Flag. */
    public final GoFeatureFlagApi api;

    /**
     * Constructor of the evaluator.
     *
     * @param api - api service to evaluate the flags
     */
    public EdgeEvaluator(GoFeatureFlagApi api) {
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
