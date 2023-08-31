package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.FlagStore;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;


public class InProcessResolver implements Resolver {
    private final FlagStore flagStore;

    public InProcessResolver(FlagdOptions options) {
        flagStore = new FlagStore(options);
    }


    @Override public void init() throws Exception {
        flagStore.init();
    }

    @Override public void shutdown() throws Exception {

    }

    // todo flag evaluations by getting flag from storage

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
