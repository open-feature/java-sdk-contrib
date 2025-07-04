package dev.openfeature.contrib.providers.prefab;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Provider implementation for Prefab.
 */
@Slf4j
public class PrefabProvider extends EventProvider {

    @Getter
    private static final String NAME = "Prefab";

    public static final String PROVIDER_NOT_YET_INITIALIZED = "provider not yet initialized";
    public static final String UNKNOWN_ERROR = "unknown error";

    private final PrefabProviderConfig prefabProviderConfig;

    @Getter
    private PrefabCloudClient prefabCloudClient;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    /**
     * Constructor.
     *
     * @param prefabProviderConfig prefabProvider Config
     */
    public PrefabProvider(PrefabProviderConfig prefabProviderConfig) {
        this.prefabProviderConfig = prefabProviderConfig;
    }

    /**
     * Initialize the provider.
     *
     * @param evaluationContext evaluation context
     * @throws Exception on error
     */
    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        boolean initialized = isInitialized.getAndSet(true);
        if (initialized) {
            throw new GeneralError("already initialized");
        }
        super.initialize(evaluationContext);
        prefabCloudClient = new PrefabCloudClient(prefabProviderConfig.getOptions());
        log.info("finished initializing provider");

        prefabProviderConfig.getOptions().addConfigChangeListener(changeEvent -> {
            ProviderEventDetails providerEventDetails = ProviderEventDetails.builder()
                    .flagsChanged(Collections.singletonList(changeEvent.getKey()))
                    .message("config changed")
                    .build();
            emitProviderConfigurationChanged(providerEventDetails);
        });
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        PrefabContextSetReadable context = ctx == null ? null : ContextTransformer.transform(ctx);
        Boolean evaluatedValue = prefabCloudClient.featureFlagClient().featureIsOn(key, context);
        return ProviderEvaluation.<Boolean>builder().value(evaluatedValue).build();
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        PrefabContextSetReadable context = ctx == null ? null : ContextTransformer.transform(ctx);
        String evaluatedValue = defaultValue;
        Optional<Prefab.ConfigValue> opt = prefabCloudClient.featureFlagClient().get(key, context);
        if (opt.isPresent()
                && Prefab.ConfigValue.TypeCase.STRING.equals(opt.get().getTypeCase())) {
            evaluatedValue = opt.get().getString();
        }
        return ProviderEvaluation.<String>builder().value(evaluatedValue).build();
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        PrefabContextSetReadable context = ctx == null ? null : ContextTransformer.transform(ctx);
        Integer evaluatedValue = defaultValue;
        Optional<Prefab.ConfigValue> opt = prefabCloudClient.featureFlagClient().get(key, context);
        if (opt.isPresent() && Prefab.ConfigValue.TypeCase.INT.equals(opt.get().getTypeCase())) {
            evaluatedValue = Math.toIntExact(opt.get().getInt());
        }
        return ProviderEvaluation.<Integer>builder().value(evaluatedValue).build();
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        PrefabContextSetReadable context = ctx == null ? null : ContextTransformer.transform(ctx);
        Double evaluatedValue = defaultValue;
        Optional<Prefab.ConfigValue> opt = prefabCloudClient.featureFlagClient().get(key, context);
        if (opt.isPresent()
                && Prefab.ConfigValue.TypeCase.DOUBLE.equals(opt.get().getTypeCase())) {
            evaluatedValue = opt.get().getDouble();
        }
        return ProviderEvaluation.<Double>builder().value(evaluatedValue).build();
    }

    @SneakyThrows
    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        String defaultValueString = defaultValue == null ? null : defaultValue.asString();
        ProviderEvaluation<String> stringEvaluation = getStringEvaluation(key, defaultValueString, ctx);
        Value evaluatedValue = new Value(stringEvaluation.getValue());
        return ProviderEvaluation.<Value>builder().value(evaluatedValue).build();
    }

    @SneakyThrows
    @Override
    public void shutdown() {
        super.shutdown();
        log.info("shutdown");
        if (prefabCloudClient != null) {
            prefabCloudClient.close();
        }
    }
}
