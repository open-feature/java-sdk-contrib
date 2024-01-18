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

        Future<Void> initFuture = Statsig.initializeAsync(statsigProviderConfig.getSdkKey(), statsigProviderConfig.getOptions());
        initFuture.get();

        statsigProviderConfig.postInit();
        state = ProviderState.READY;
        log.info("finished initializing provider, state: {}", state);
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
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
            ConfigKey configKey = parseConfigKeys(key);
            String evaluatedValue = defaultValue;
            switch (configKey.getType()) {
                case CONFIG:
                    DynamicConfig dynamicConfig = Statsig.getConfigAsync(user, configKey.getName()).get();
                    evaluatedValue = dynamicConfig.getString(configKey.getKey(), defaultValue);
                    break;
                case LAYER:
                    Layer layer = Statsig.getLayerAsync(user, configKey.getName()).get();
                    evaluatedValue = layer.getString(configKey.getKey(), defaultValue);
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
            ConfigKey configKey = parseConfigKeys(key);
            Integer evaluatedValue = defaultValue;
            switch (configKey.getType()) {
                case CONFIG:
                    DynamicConfig dynamicConfig = Statsig.getConfigAsync(user, configKey.getName()).get();
                    evaluatedValue = dynamicConfig.getInt(configKey.getKey(), defaultValue);
                    break;
                case LAYER:
                    Layer layer = Statsig.getLayerAsync(user, configKey.getName()).get();
                    evaluatedValue = layer.getInt(configKey.getKey(), defaultValue);
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
            ConfigKey configKey = parseConfigKeys(key);
            Double evaluatedValue = defaultValue;
            switch (configKey.getType()) {
                case CONFIG:
                    DynamicConfig dynamicConfig = Statsig.getConfigAsync(user, configKey.getName()).get();
                    evaluatedValue = dynamicConfig.getDouble(configKey.getKey(), defaultValue);
                    break;
                case LAYER:
                    Layer layer = Statsig.getLayerAsync(user, configKey.getName()).get();
                    evaluatedValue = layer.getDouble(configKey.getKey(), defaultValue);
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
            ConfigKey configKey = parseConfigKeys(key);
            String evaluatedValue = defaultValue.asString();
            switch (configKey.getType()) {
                case CONFIG:
                    DynamicConfig dynamicConfig = Statsig.getConfigAsync(user, configKey.getName()).get();
                    evaluatedValue = dynamicConfig.getString(configKey.getKey(), defaultValue.asString());
                    break;
                case LAYER:
                    Layer layer = Statsig.getLayerAsync(user, configKey.getName()).get();
                    evaluatedValue = layer.getString(configKey.getKey(), defaultValue.asString());
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

    @AllArgsConstructor
    @Getter
    private static class ConfigKey {
        private enum Type {
            CONFIG, LAYER
        }
        private Type type;
        private String Name;
        private String key;
    }

    /**
     * parse dynamic config name and key from feature key.
     * @param key configuration key, for example 'config.product.name' or 'layer.product.name'.
     * @return ConfigKey dynamic config name and key
     */
    @NotNull
    private static ConfigKey parseConfigKeys(String key) {
        String[] keys = key.split("\\.");
        if (keys.length != 3) {
            throw new IllegalArgumentException("configuration key must contain exactly two occurrence of '.' character, for example 'config.product.name'.");
        }
        return new ConfigKey(ConfigKey.Type.valueOf(keys[0].toUpperCase()), keys[1], keys[2]);
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
