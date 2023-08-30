package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;


public class InProcessResolver implements Resolver {
    private final FlagdOptions options;

    public InProcessResolver(FlagdOptions options) {
        this.options = options;
    }


    @Override public void init() throws Exception {
    }

    @Override public void shutdown() throws Exception {

    }

    @Override public ProviderEvaluation<Boolean> booleanEvaluation(String key, Boolean defaultValue,
                                                                   EvaluationContext ctx) {
        return null;
    }

    @Override public ProviderEvaluation<String> stringEvaluation(String key, String defaultValue,
                                                                 EvaluationContext ctx) {
        return null;
    }

    @Override public ProviderEvaluation<Double> doubleEvaluation(String key, Double defaultValue,
                                                                 EvaluationContext ctx) {
        return null;
    }

    @Override public ProviderEvaluation<Integer> integerEvaluation(String key, Integer defaultValue,
                                                                   EvaluationContext ctx) {
        return null;
    }

    @Override public ProviderEvaluation<Value> objectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return null;
    }
}
