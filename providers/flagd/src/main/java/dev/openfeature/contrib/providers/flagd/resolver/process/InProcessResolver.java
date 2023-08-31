package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.FlagModel;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.FlagStore;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;


public class InProcessResolver implements Resolver {
    private final FlagStore flagStore;

    public InProcessResolver(FlagdOptions options) {
        flagStore = new FlagStore(options);
    }


    @Override
    public void init() throws Exception {
        flagStore.init();
    }

    @Override
    public void shutdown() throws Exception {

    }

    @Override
    public ProviderEvaluation<Boolean> booleanEvaluation(String key, Boolean defaultValue,
                                                                   EvaluationContext ctx) {
        return resolveGeneric(Boolean.class, key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<String> stringEvaluation(String key, String defaultValue,
                                                                 EvaluationContext ctx) {
        return resolveGeneric(String.class, key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Double> doubleEvaluation(String key, Double defaultValue,
                                                                 EvaluationContext ctx) {
        return resolveGeneric(Double.class, key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Integer> integerEvaluation(String key, Integer defaultValue,
                                                                   EvaluationContext ctx) {
        return resolveGeneric(Integer.class, key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Value> objectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return resolveGeneric(Value.class, key, defaultValue, ctx);
    }

    private <T> ProviderEvaluation<T> resolveGeneric(Class<T> type, String key, T defaultValue,
                                                               EvaluationContext ctx){
       final FlagModel flag = flagStore.getFLag(key);

        if (flag == null){
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason(Reason.ERROR.toString())
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .errorMessage(String.format("requested flag could not be found: %s", key))
                    .build();
        }

        // state check
        if ("DISABLED".equals(flag.getState())){
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason(Reason.ERROR.toString())
                    .errorCode(ErrorCode.GENERAL)
                    .errorMessage(String.format("requested flag is disabled: %s", key))
                    .build();
        }

        // todo - handle targeting rule


        Object resolvedVariant = flag.getVariants().get(flag.getDefaultVariant());

        if (!resolvedVariant.getClass().isAssignableFrom(type)){
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason(Reason.ERROR.toString())
                    .errorCode(ErrorCode.TYPE_MISMATCH)
                    .errorMessage(String.format("requested flag is not of type: %s", key))
                    .build();
        }

        return ProviderEvaluation.<T>builder()
                .value((T) resolvedVariant)
                .reason(Reason.STATIC.toString())
                .build();
    }


}
