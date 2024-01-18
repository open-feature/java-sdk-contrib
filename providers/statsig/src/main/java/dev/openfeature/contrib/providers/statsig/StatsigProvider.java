package dev.openfeature.contrib.providers.statsig;

import com.statsig.sdk.DynamicConfig;
import com.statsig.sdk.Layer;
import com.statsig.sdk.Statsig;
import com.statsig.sdk.StatsigUser;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provider implementation for Statsig.
 */
@Slf4j
public class StatsigProvider extends EventProvider {

    @Getter
    private static final String NAME = "Statsig";

    public static final String PROVIDER_NOT_YET_INITIALIZED = "provider not yet initialized";
    public static final String UNKNOWN_ERROR = "unknown error";
    public static final String FEATURE_CONFIG_KEY = "feature_config";

    private final StatsigProviderConfig statsigProviderConfig;

    @Getter
    private ProviderState state = ProviderState.NOT_READY;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    /**
     * Constructor.
     * @param statsigProviderConfig statsigProvider Config
     */
    public StatsigProvider(StatsigProviderConfig statsigProviderConfig) {
        this.statsigProviderConfig = statsigProviderConfig;
    }

    /**
     * Initialize the provider.
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

        Future<Void> initFuture = Statsig.initializeAsync(statsigProviderConfig.getSdkKey(),
            statsigProviderConfig.getOptions());
        initFuture.get();

        statsigProviderConfig.postInit();
        state = ProviderState.READY;
        log.info("finished initializing provider, state: {}", state);
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    @AllArgsConstructor
    @Getter
    public static class FeatureConfig {
        public enum Type {
            CONFIG, LAYER
        }

        private Type type;
        private String name;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        if (!ProviderState.READY.equals(state)) {
            if (ProviderState.NOT_READY.equals(state)) {
                throw new ProviderNotReadyError(PROVIDER_NOT_YET_INITIALIZED);
            }
            throw new GeneralError(UNKNOWN_ERROR);
        }
        StatsigUser user = ContextTransformer.transform(ctx);
        Future<Boolean> featureOn = Statsig.checkGateAsync(user, key);
        try {
            Boolean evaluatedValue = featureOn.get();
            return ProviderEvaluation.<Boolean>builder()
                .value(evaluatedValue)
                .build();
        } catch (Exception e) {
            log.error("Error evaluating boolean", e);
            throw new GeneralError(e.getMessage());
        }
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        if (!ProviderState.READY.equals(state)) {
            if (ProviderState.NOT_READY.equals(state)) {
                throw new ProviderNotReadyError(PROVIDER_NOT_YET_INITIALIZED);
            }
            throw new GeneralError(UNKNOWN_ERROR);
        }
        StatsigUser user = ContextTransformer.transform(ctx);
        try {
            FeatureConfig featureConfig = parseFeatureConfig(ctx);
            String evaluatedValue = defaultValue;
            switch (featureConfig.getType()) {
                case CONFIG:
                    DynamicConfig dynamicConfig = Statsig.getConfigAsync(user, featureConfig.getName()).get();
                    evaluatedValue = dynamicConfig.getString(key, defaultValue);
                    break;
                case LAYER:
                    Layer layer = Statsig.getLayerAsync(user, featureConfig.getName()).get();
                    evaluatedValue = layer.getString(key, defaultValue);
                    break;
                default:
                    break;
            }
            return ProviderEvaluation.<String>builder()
                .value(evaluatedValue)
                .build();
        } catch (Exception e) {
            log.error("Error evaluating string", e);
            throw new GeneralError(e.getMessage());
        }
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        if (!ProviderState.READY.equals(state)) {
            if (ProviderState.NOT_READY.equals(state)) {
                throw new ProviderNotReadyError(PROVIDER_NOT_YET_INITIALIZED);
            }
            throw new GeneralError(UNKNOWN_ERROR);
        }
        StatsigUser user = ContextTransformer.transform(ctx);
        try {
            FeatureConfig featureConfig = parseFeatureConfig(ctx);
            Integer evaluatedValue = defaultValue;
            switch (featureConfig.getType()) {
                case CONFIG:
                    DynamicConfig dynamicConfig = Statsig.getConfigAsync(user, featureConfig.getName()).get();
                    evaluatedValue = dynamicConfig.getInt(key, defaultValue);
                    break;
                case LAYER:
                    Layer layer = Statsig.getLayerAsync(user, featureConfig.getName()).get();
                    evaluatedValue = layer.getInt(key, defaultValue);
                    break;
                default:
                    break;
            }
            return ProviderEvaluation.<Integer>builder()
                .value(evaluatedValue)
                .build();
        } catch (Exception e) {
            log.error("Error evaluating integer", e);
            throw new GeneralError(e.getMessage());
        }
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        if (!ProviderState.READY.equals(state)) {
            if (ProviderState.NOT_READY.equals(state)) {
                throw new ProviderNotReadyError(PROVIDER_NOT_YET_INITIALIZED);
            }
            throw new GeneralError(UNKNOWN_ERROR);
        }
        StatsigUser user = ContextTransformer.transform(ctx);
        try {
            FeatureConfig featureConfig = parseFeatureConfig(ctx);
            Double evaluatedValue = defaultValue;
            switch (featureConfig.getType()) {
                case CONFIG:
                    DynamicConfig dynamicConfig = Statsig.getConfigAsync(user, featureConfig.getName()).get();
                    evaluatedValue = dynamicConfig.getDouble(key, defaultValue);
                    break;
                case LAYER:
                    Layer layer = Statsig.getLayerAsync(user, featureConfig.getName()).get();
                    evaluatedValue = layer.getDouble(key, defaultValue);
                    break;
                default:
                    break;
            }
            return ProviderEvaluation.<Double>builder()
                .value(evaluatedValue)
                .build();
        } catch (Exception e) {
            log.error("Error evaluating double", e);
            throw new GeneralError(e.getMessage());
        }
    }

    @SneakyThrows
    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        if (!ProviderState.READY.equals(state)) {
            if (ProviderState.NOT_READY.equals(state)) {
                throw new ProviderNotReadyError(PROVIDER_NOT_YET_INITIALIZED);
            }
            throw new GeneralError(UNKNOWN_ERROR);
        }
        StatsigUser user = ContextTransformer.transform(ctx);
        try {
            FeatureConfig featureConfig = parseFeatureConfig(ctx);
            String evaluatedValue = defaultValue.asString();
            switch (featureConfig.getType()) {
                case CONFIG:
                    DynamicConfig dynamicConfig = Statsig.getConfigAsync(user, featureConfig.getName()).get();
                    evaluatedValue = dynamicConfig.getString(key, defaultValue.asString());
                    break;
                case LAYER:
                    Layer layer = Statsig.getLayerAsync(user, featureConfig.getName()).get();
                    evaluatedValue = layer.getString(key, defaultValue.asString());
                    break;
                default:
                    break;
            }
            return ProviderEvaluation.<Value>builder()
                .value(Value.objectToValue(evaluatedValue))
                .build();
        } catch (Exception e) {
            log.error("Error evaluating object", e);
            throw new GeneralError(e.getMessage());
        }
    }

    @NotNull
    private static FeatureConfig parseFeatureConfig(EvaluationContext ctx) {
        Value featureConfigValue = ctx.getValue(FEATURE_CONFIG_KEY);
        if (featureConfigValue == null) {
            throw new IllegalArgumentException("feature config not found at evaluation context.");
        }
        if (!featureConfigValue.isStructure()) {
            throw new IllegalArgumentException("feature config is not a structure.");
        }
        Structure featureConfigStructure = featureConfigValue.asStructure();
        Value typeValue = featureConfigStructure.getValue("type");
        if (typeValue == null) {
            throw new IllegalArgumentException("feature config type is missing");
        }
        FeatureConfig.Type type = FeatureConfig.Type.valueOf(typeValue.asString());
        Value nameValue = featureConfigStructure.getValue("name");
        if (nameValue == null) {
            throw new IllegalArgumentException("feature config name is missing");
        }
        String name = nameValue.asString();
        return new FeatureConfig(type, name);
    }

    @SneakyThrows
    @Override
    public void shutdown() {
        super.shutdown();
        log.info("shutdown");
        Statsig.shutdown();
        state = ProviderState.NOT_READY;
    }
}
