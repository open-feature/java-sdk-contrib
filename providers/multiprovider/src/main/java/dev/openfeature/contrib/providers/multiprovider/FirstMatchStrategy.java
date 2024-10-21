package dev.openfeature.contrib.providers.multiprovider;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static dev.openfeature.sdk.ErrorCode.FLAG_NOT_FOUND;

/**
 * First match strategy.
 */
public class FirstMatchStrategy extends BaseStrategy {

    public FirstMatchStrategy(Map<String, FeatureProvider> providers) {
        super(providers);
    }

    /**
     * Represents a strategy that evaluates providers based on a first-match approach.
     * Provides a method to evaluate providers using a specified function and return the evaluation result.
     *
     * @param providerFunction provider function
     * @param <T> ProviderEvaluation type
     * @return the provider evaluation
     */
    @Override
    public <T> ProviderEvaluation<T> evaluate(String key, T defaultValue, EvaluationContext ctx, Function<FeatureProvider, ProviderEvaluation<T>> providerFunction) {
        for (FeatureProvider provider: getProviders().values()) {
            ProviderEvaluation<T> result;
            try {
                result = providerFunction.apply(provider);
            } catch (FlagNotFoundError e) {
                continue;
            }
            return result;
        }

        throw new FlagNotFoundError("flag not found");
    }
}
