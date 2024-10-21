package dev.openfeature.contrib.providers.multiprovider;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static dev.openfeature.sdk.ErrorCode.GENERAL;

/**
 * First Successful Strategy.
 */
public class FirstSuccessfulStrategy extends BaseStrategy {

    public FirstSuccessfulStrategy(Map<String, FeatureProvider> providers) {
        super(providers);
    }

    @Override
    public <T> ProviderEvaluation<T> evaluate(String key, T defaultValue, EvaluationContext ctx, Function<FeatureProvider, ProviderEvaluation<T>> providerFunction) {
        for (FeatureProvider provider: getProviders().values()) {
            ProviderEvaluation<T> result;
            try {
                result = providerFunction.apply(provider);
            } catch (Exception e) {
                continue;
            }
            return result;
        }

        throw new GeneralError("evaluation error");
    }
}
