package dev.openfeature.contrib.tools.junitopenfeature;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
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
    private static final InMemoryProvider FALLBACK_PROVIDER = createProvider(new HashMap<>());

    private static final ThreadLocal<ExtensionContext.Namespace> CURRENT_NAMESPACE = new ThreadLocal<>();

    @Getter
    private static final String NAME = "TestingProvider";

    private final Map<ExtensionContext.Namespace, InMemoryProvider> providerMap = new HashMap<>();

    public TestProvider(ExtensionContext.Namespace namespace, Map<String, Flag<?>> flags) {
        providerMap.put(namespace, createProvider(flags));
    }

    @SneakyThrows
    private static InMemoryProvider createProvider(Map<String, Flag<?>> flags) {
        InMemoryProvider inMemoryProvider = new InMemoryProvider(flags);
        inMemoryProvider.initialize(new ImmutableContext());
        return inMemoryProvider;
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    /**
     * Updating provider flags configuration, replacing existing flags.
     *
     * @param flags the flags to use instead of the previous flags.
     */
    public void addConfigurationForTest(ExtensionContext.Namespace namespace, Map<String, Flag<?>> flags) {
        InMemoryProvider inMemoryProvider = providerMap.putIfAbsent(namespace, createProvider(flags));

        if (inMemoryProvider != null) {
            inMemoryProvider.updateFlags(flags);
        }
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue,
                                                            EvaluationContext evaluationContext) {
        return providerMap
                .getOrDefault(CURRENT_NAMESPACE.get(), FALLBACK_PROVIDER)
                .getBooleanEvaluation(key, defaultValue, evaluationContext);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue,
                                                          EvaluationContext evaluationContext) {

        return providerMap
                .getOrDefault(CURRENT_NAMESPACE.get(), FALLBACK_PROVIDER)
                .getStringEvaluation(key, defaultValue, evaluationContext);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue,
                                                            EvaluationContext evaluationContext) {
        return providerMap
                .getOrDefault(CURRENT_NAMESPACE.get(), FALLBACK_PROVIDER)
                .getIntegerEvaluation(key, defaultValue, evaluationContext);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue,
                                                          EvaluationContext evaluationContext) {
        return providerMap
                .getOrDefault(CURRENT_NAMESPACE.get(), FALLBACK_PROVIDER)
                .getDoubleEvaluation(key, defaultValue, evaluationContext);
    }

    @SneakyThrows
    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue,
                                                         EvaluationContext evaluationContext) {
        return providerMap
                .getOrDefault(CURRENT_NAMESPACE.get(), FALLBACK_PROVIDER)
                .getObjectEvaluation(key, defaultValue, evaluationContext);
    }

    @Override
    public ProviderState getState() {
        return providerMap
                .values()
                .stream()
                .map(InMemoryProvider::getState)
                .reduce(
                        (providerState, providerState2) -> {
                            if (providerState.ordinal() < providerState2.ordinal()) {
                                return providerState2;
                            }
                            return providerState;
                        }
                )
                .orElse(ProviderState.READY);
    }

    public static void setCurrentNamespace(ExtensionContext.Namespace namespace) {
        CURRENT_NAMESPACE.set(namespace);
    }

    public static void clearCurrentNamespace() {
        CURRENT_NAMESPACE.remove();
    }
}
