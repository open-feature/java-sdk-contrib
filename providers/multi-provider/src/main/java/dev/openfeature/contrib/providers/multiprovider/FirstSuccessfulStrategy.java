package dev.openfeature.contrib.providers.multiprovider;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ProviderEvaluation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static dev.openfeature.sdk.ErrorCode.GENERAL;

public class FirstSuccessfulStrategy extends BaseStrategy{

    public FirstSuccessfulStrategy(Map<String, FeatureProvider> providers) {
        super(providers);
    }

    public <T> ProviderEvaluation<T> evaluate(Function<FeatureProvider, ProviderEvaluation<T>> providerFunction) {
        for (FeatureProvider provider: getProviders().values()) {
            ProviderEvaluation<T> result = providerFunction.apply(provider);
            if (result.getErrorCode() != null) {
                return result;
            }
        }

        return ProviderEvaluation.<T>builder()
            .reason(GENERAL.name())
            .build();
    }
}
