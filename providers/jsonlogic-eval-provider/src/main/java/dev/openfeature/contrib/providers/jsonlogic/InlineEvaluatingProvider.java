package dev.openfeature.contrib.providers.jsonlogic;

import dev.openfeature.sdk.*;
import dev.openfeature.sdk.exceptions.ParseError;
import io.github.jamsesso.jsonlogic.JsonLogic;
import io.github.jamsesso.jsonlogic.JsonLogicException;

import java.util.function.Function;

public class InlineEvaluatingProvider implements FeatureProvider {
    private final JsonLogic logic;
    private final RuleFetcher fetcher;


    public void initialize(EvaluationContext initialContext) {
        fetcher.initialize(initialContext);
    }

    public InlineEvaluatingProvider(JsonLogic logic, RuleFetcher fetcher) {
        this.logic = logic;
        this.fetcher = fetcher;
    }

    @Override
    public Metadata getMetadata() {
        return null;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean _default, EvaluationContext ctx) {
        return evalRuleForKey(key, _default, ctx);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String _default, EvaluationContext ctx) {
        return evalRuleForKey(key, _default, ctx);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer _default, EvaluationContext ctx) {
        // jsonlogic only returns doubles, not integers.
        return evalRuleForKey(key, _default, ctx, (o) -> ((Double) o).intValue());
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double _default, EvaluationContext ctx) {
        return evalRuleForKey(key, _default, ctx);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String s, Value value, EvaluationContext evaluationContext) {
        // we can't use the common implementation because we need to convert to-and-from Value objects.
        throw new UnsupportedOperationException("Haven't gotten there yet.");
//        return evalRuleForKey(key, _default, ctx, (o) -> o);
    }

    private <T> ProviderEvaluation<T> evalRuleForKey(String key, T _default, EvaluationContext ctx) {
        return evalRuleForKey(key, _default, ctx, (o) -> (T) o);
    }
    private <T> ProviderEvaluation<T> evalRuleForKey(String key, T _default, EvaluationContext ctx, Function<Object, T> resultToType) {
        String rule = fetcher.getRuleForKey(key);
        if (rule == null) {
            return ProviderEvaluation.<T>builder()
                    .value(_default)
                    .reason("Unable to find rules for the given key")
                    .build();
        }

        try {
            return ProviderEvaluation.<T>builder()
                    .value(resultToType.apply(this.logic.apply(rule, ctx.asObjectMap())))
                    .build();
        } catch (JsonLogicException e) {
            throw new ParseError(e);
        }
    }
}
