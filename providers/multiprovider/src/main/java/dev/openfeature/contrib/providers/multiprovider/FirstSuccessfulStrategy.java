package dev.openfeature.contrib.providers.multiprovider;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.exceptions.GeneralError;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Function;

/**
 * First Successful Strategy.
 * Similar to “First Match”, except that errors from evaluated providers do not halt execution.
 * Instead, it will return the first successful result from a provider.
 * If no provider successfully responds, it will throw an error result.
 */
@Slf4j
public class FirstSuccessfulStrategy extends BaseStrategy {

    public FirstSuccessfulStrategy(Map<String, FeatureProvider> providers) {
        super(providers);
    }

    @Override
    public <T> ProviderEvaluation<T> evaluate(String key, T defaultValue, EvaluationContext ctx,
              Function<FeatureProvider, ProviderEvaluation<T>> providerFunction) {
        for (FeatureProvider provider: getProviders().values()) {
            try {
                return providerFunction.apply(provider);
            } catch (Exception e) {
                log.debug("flag not found {}", e.getMessage());
            }
        }

        throw new GeneralError("evaluation error");
    }
}
