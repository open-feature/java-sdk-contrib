package dev.openfeature.contrib.providers.gofeatureflag;

import dev.openfeature.sdk.ProviderEvaluation;
import lombok.Builder;
import lombok.Getter;

/**
 * EvaluationResponse wrapping the provider evaluation.
 * @param <T> evaluation type
 */
@Builder
@Getter
public class EvaluationResponse<T> {
    private ProviderEvaluation<T> providerEvaluation;
    private Boolean cachable;
}
