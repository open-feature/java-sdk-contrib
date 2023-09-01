package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.FlagStore;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import io.github.jamsesso.jsonlogic.JsonLogic;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import lombok.extern.java.Log;

import java.util.logging.Level;

@Log
public class InProcessResolver implements Resolver {
    private final FlagStore flagStore;
    private final JsonLogic jsonLogicHandler;

    public InProcessResolver(FlagdOptions options) {
        flagStore = new FlagStore(options);
        jsonLogicHandler = new JsonLogic();

        // todo - custom json logic operators
    }


    @Override
    public void init() throws Exception {
        flagStore.init();
    }

    @Override
    public void shutdown() {
        flagStore.shutdown();
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
                                                     EvaluationContext ctx) {
        final FeatureFlag flag = flagStore.getFLag(key);

        if (flag == null) {
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason(Reason.ERROR.toString())
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .errorMessage(String.format("requested flag could not be found: %s", key))
                    .build();
        }

        // state check
        if ("DISABLED".equals(flag.getState())) {
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason(Reason.ERROR.toString())
                    .errorCode(ErrorCode.GENERAL)
                    .errorMessage(String.format("requested flag is disabled: %s", key))
                    .build();
        }


        final Object resolvedVariant;
        final String reason;

        if (flag.getTargeting() != "{}") {
            try {
                resolvedVariant = jsonLogicHandler.apply(flag.getTargeting(), ctx.asObjectMap());
                reason = Reason.TARGETING_MATCH.toString();
            } catch (JsonLogicException e) {
                log.log(Level.INFO, "Error evaluating targeting rule", e);
                return ProviderEvaluation.<T>builder()
                        .value(defaultValue)
                        .reason(Reason.ERROR.toString())
                        .errorCode(ErrorCode.PARSE_ERROR)
                        .errorMessage(String.format("error parsing targeting rule: %s", key))
                        .build();
            }
        } else {
            resolvedVariant = flag.getVariants().get(flag.getDefaultVariant());
            reason = Reason.STATIC.toString();
        }

        if (!resolvedVariant.getClass().isAssignableFrom(type)) {
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason(Reason.ERROR.toString())
                    .errorCode(ErrorCode.TYPE_MISMATCH)
                    .errorMessage(String.format("requested flag is not of type: %s", key))
                    .build();
        }

        return ProviderEvaluation.<T>builder()
                .value((T) resolvedVariant)
                .reason(reason)
                .build();
    }


}
