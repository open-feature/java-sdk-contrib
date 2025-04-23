package dev.openfeature.contrib.providers.v2.gofeatureflag.controller;

import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.sdk.EvaluationContext;

public interface IEvaluator {
    void init();

    void destroy();

    GoFeatureFlagResponse evaluate(String key, Object defaultValue, EvaluationContext evaluationContext);
}
