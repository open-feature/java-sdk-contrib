package dev.openfeature.contrib.providers.multiprovider;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Function;

/**
 * First match strategy.
 * Return the first result returned by a provider. Skip providers that indicate they had no value due to
 * FLAG_NOT_FOUND.
 * In all other cases, use the value returned by the provider.
 * If any provider returns an error result other than FLAG_NOT_FOUND, the whole evaluation should error and
 * “bubble up” the individual provider’s error in the result.
 * As soon as a value is returned by a provider, the rest of the operation should short-circuit and not call
 * the rest of the providers.
 */
@Slf4j
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
    public <T> ProviderEvaluation<T> evaluate(String key, T defaultValue, EvaluationContext ctx,
              Function<FeatureProvider, ProviderEvaluation<T>> providerFunction) {
        for (FeatureProvider provider: getProviders().values()) {
            try {
                return providerFunction.apply(provider);
            } catch (FlagNotFoundError e) {
                log.debug("flag not found {}", e.getMessage());
            }
        }

        throw new FlagNotFoundError("flag not found");
    }
}
