package dev.openfeature.contrib.providers.flagsmith;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;

import java.util.List;

class FlagsmithProvider implements FeatureProvider {

    private static FlagsmithClient flagsmith;

    public FlagsmithProvider(){
        this.flagsmith = FlagsmithClient
            .newBuilder()
            .setApiKey(System.getenv("<FLAGSMITH_SERVER_SIDE_ENVIRONMENT_KEY>"))
            .build();
    }

    @Override
    public Metadata getMetadata() {
        return null;
    }

    @Override
    public List<Hook> getProviderHooks() {
        return FeatureProvider.super.getProviderHooks();
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(
        String s, Boolean aBoolean, EvaluationContext evaluationContext) {
        return null;
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(
        String s, String s1, EvaluationContext evaluationContext) {
        return null;
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(
        String s, Integer integer, EvaluationContext evaluationContext) {
        return null;
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(
        String s, Double aDouble, EvaluationContext evaluationContext) {
        return null;
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(
        String s, Value value, EvaluationContext evaluationContext) {
        return null;
    }
}