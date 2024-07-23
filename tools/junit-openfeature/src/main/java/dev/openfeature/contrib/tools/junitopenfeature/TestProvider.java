package dev.openfeature.contrib.tools.junitopenfeature;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.OpenFeatureError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import dev.openfeature.sdk.providers.memory.Flag;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.HashMap;
import java.util.Map;

/**
 * TestProvider based on InMemoryProvider but with another dimension added to the maps of flags.
 */
@Slf4j
public class TestProvider extends EventProvider {
    public static final ThreadLocal<ExtensionContext.Namespace> CURRENT_NAMESPACE = new ThreadLocal<>();
    public Map<ExtensionContext.Namespace, Map<String, Flag<?>>> flagsMap = new HashMap<>();

    @Getter
    private static final String NAME = "TestingProvider";

    @Getter
    private ProviderState state = ProviderState.NOT_READY;

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    public TestProvider(ExtensionContext.Namespace namespace, Map<String, Flag<?>> flags) {
        flagsMap.put(namespace, flags);
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

    /**
     * Updating provider flags configuration, replacing existing flags.
     * @param flags the flags to use instead of the previous flags.
     */
    public void addFlags(ExtensionContext.Namespace namespace, Map<String, Flag<?>> flags) {
        flagsMap.put(namespace, flags);
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue,
                                                            EvaluationContext evaluationContext) {
        return getEvaluation(key, evaluationContext, Boolean.class);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue,
                                                          EvaluationContext evaluationContext) {
        return getEvaluation(key, evaluationContext, String.class);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue,
                                                            EvaluationContext evaluationContext) {
        return getEvaluation(key, evaluationContext, Integer.class);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue,
                                                          EvaluationContext evaluationContext) {
        return getEvaluation(key, evaluationContext, Double.class);
    }

    @SneakyThrows
    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue,
                                                         EvaluationContext evaluationContext) {
        return getEvaluation(key, evaluationContext, Value.class);
    }

    @SuppressWarnings("unchecked")
    private <T> ProviderEvaluation<T> getEvaluation(
            String key, EvaluationContext evaluationContext, Class<?> expectedType
    ) throws OpenFeatureError {
        if (!ProviderState.READY.equals(state)) {
            if (ProviderState.NOT_READY.equals(state)) {
                throw new ProviderNotReadyError("provider not yet initialized");
            }
            throw new GeneralError("unknown error");
        }
        Map<ExtensionContext.Namespace, Map<String, Flag<?>>> flagsMap1 = flagsMap;
        ExtensionContext.Namespace key1 = CURRENT_NAMESPACE.get();
        Flag<?> flag = flagsMap1.getOrDefault(key1, new HashMap<>()).get(key);
        if (flag == null) {
            throw new FlagNotFoundError("flag " + key + "not found");
        }
        T value;
        if (flag.getContextEvaluator() != null) {
            value = (T) flag.getContextEvaluator().evaluate(flag, evaluationContext);
        } else if (!expectedType.isInstance(flag.getVariants().get(flag.getDefaultVariant()))) {
            throw new TypeMismatchError("flag " + key + "is not of expected type");
        } else {
            value = (T) flag.getVariants().get(flag.getDefaultVariant());
        }
        return ProviderEvaluation.<T>builder()
                .value(value)
                .variant(flag.getDefaultVariant())
                .reason(Reason.STATIC.toString())
                .build();
    }
}
