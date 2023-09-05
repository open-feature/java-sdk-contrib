package dev.openfeature.contrib.providers.togglz;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.togglz.core.Feature;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Provider implementation for Togglz.
 */
@AllArgsConstructor
@Slf4j
public class TogglzProvider extends EventProvider {

    @Getter
    private static final String NAME = "Togglz Provider";
    public static final String NOT_IMPLEMENTED =
        "Not implemented - provider does not support this type. Only boolean is supported.";

    private Map<String, Feature> features = new HashMap<>();

    @Getter
    private ProviderState state = ProviderState.NOT_READY;

    public TogglzProvider(Collection<Feature> featuresCollection) {
        featuresCollection.forEach(feature -> features.put(feature.name(), feature));
    }

    /**
     * Initialize the provider.
     * @param evaluationContext evaluation context
     * @throws Exception on error
     */
    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        super.initialize(evaluationContext);
        state = ProviderState.READY;
        log.debug("finished initializing provider, state: {}", state);
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        if (!ProviderState.READY.equals(state)) {
            if (ProviderState.NOT_READY.equals(state)) {
                throw new ProviderNotReadyError("provider not yet initialized");
            }
            throw new GeneralError("unknown error");
        }
        Feature feature = features.get(key);
        if (feature == null) {
            throw new FlagNotFoundError("flag " + key + " not found");
        }
        boolean featureBooleanValue = feature.isActive();
        return ProviderEvaluation.<Boolean>builder()
            .value(featureBooleanValue)
            .reason(Reason.TARGETING_MATCH.name())
            .build();
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        throw new TypeMismatchError(NOT_IMPLEMENTED);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        throw new TypeMismatchError(NOT_IMPLEMENTED);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        throw new TypeMismatchError(NOT_IMPLEMENTED);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String s, Value value, EvaluationContext evaluationContext) {
        throw new TypeMismatchError(NOT_IMPLEMENTED);
    }
}
