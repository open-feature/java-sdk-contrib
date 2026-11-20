package dev.openfeature.contrib.providers.jsonlogic;

import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.ParseError;
import io.github.jamsesso.jsonlogic.JsonLogic;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import java.util.function.Function;

/** A provider which evaluates JsonLogic rules provided by a {@link RuleFetcher}. */
public class JsonlogicProvider implements FeatureProvider {
    private final JsonLogic logic;
    private final RuleFetcher fetcher;

    public void initialize(EvaluationContext initialContext) {
        fetcher.initialize(initialContext);
    }

    public JsonlogicProvider(RuleFetcher fetcher) {
        this.logic = new JsonLogic();
        this.fetcher = fetcher;
    }

    public JsonlogicProvider(JsonLogic logic, RuleFetcher fetcher) {
        this.logic = logic;
        this.fetcher = fetcher;
    }

    @Override
    public Metadata getMetadata() {
        return () -> "JsonLogicProvider(" + this.fetcher.getClass().getName() + ")";
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return evalRuleForKey(key, defaultValue, ctx, Boolean.class, Boolean.class::cast);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return evalRuleForKey(key, defaultValue, ctx, String.class, String.class::cast);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        // jsonlogic only returns doubles, not integers.
        return evalRuleForKey(key, defaultValue, ctx, Number.class, (o) -> ((Number) o).intValue());
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return evalRuleForKey(key, defaultValue, ctx, Number.class, (o) -> ((Number) o).doubleValue());
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String s, Value value, EvaluationContext evaluationContext) {
        // we can't use the common implementation because we need to convert to-and-from Value objects.
        throw new UnsupportedOperationException("Haven't gotten there yet.");
    }

    private <T> ProviderEvaluation<T> evalRuleForKey(
            String key, T defaultValue, EvaluationContext ctx, Class<?> resultType, Function<Object, T> resultToType) {
        String rule = fetcher.getRuleForKey(key);
        if (rule == null) {
            return error(defaultValue, ErrorCode.FLAG_NOT_FOUND, "Unable to find rules for the given key");
        }

        Object result;
        try {
            result = this.logic.apply(rule, ctx.asObjectMap());
        } catch (JsonLogicException e) {
            throw new ParseError(e);
        }

        if (!resultType.isInstance(result)) {
            String actual = result == null ? "null" : result.getClass().getSimpleName();
            return error(
                    defaultValue,
                    ErrorCode.TYPE_MISMATCH,
                    "Rule for key " + key + " returned " + actual + ", expected " + resultType.getSimpleName());
        }

        return ProviderEvaluation.<T>builder().value(resultToType.apply(result)).build();
    }

    private static <T> ProviderEvaluation<T> error(T defaultValue, ErrorCode errorCode, String errorMessage) {
        return ProviderEvaluation.<T>builder()
                .value(defaultValue)
                .reason(Reason.ERROR.toString())
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
