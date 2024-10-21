package dev.openfeature.contrib.providers.multiprovider;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.exceptions.GeneralError;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Function;

/**
 * First Successful Strategy.
 * Similar to “First Match”, except that errors from evaluated providers do not halt execution.
 * Instead, it will return the first successful result from a provider.
 * If no provider successfully responds, it will throw an error result.
 */
@Slf4j
public class FirstSuccessfulStrategy extends BaseStrategy {

    public FirstSuccessfulStrategy(List<FeatureProvider> providers) {
        super(providers);
    }

    @Override
    public <T> ProviderEvaluation<T> evaluate(String key, T defaultValue, EvaluationContext ctx,
              Function<FeatureProvider, ProviderEvaluation<T>> providerFunction) {
        for (FeatureProvider provider: getProviders().values()) {
            try {
                ProviderEvaluation<T> res = providerFunction.apply(provider);
                if (res.getErrorCode() == null) {
                    return res;
                }
            } catch (Exception e) {
                log.debug("evaluation exception {}", e.getMessage());
            }
        }

        throw new GeneralError("evaluation error");
    }
}
