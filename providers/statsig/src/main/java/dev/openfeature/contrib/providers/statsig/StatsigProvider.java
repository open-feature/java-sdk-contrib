package dev.openfeature.contrib.providers.statsig;

import com.statsig.sdk.APIFeatureGate;
import com.statsig.sdk.DynamicConfig;
import com.statsig.sdk.EvaluationReason;
import com.statsig.sdk.Layer;
import com.statsig.sdk.Statsig;
import com.statsig.sdk.StatsigUser;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/** Provider implementation for Statsig. */
@Slf4j
public class StatsigProvider extends EventProvider {

    @Getter
    private static final String NAME = "Statsig";

    private static final String FEATURE_CONFIG_KEY = "feature_config";
    private final StatsigProviderConfig statsigProviderConfig;

    /**
     * Constructor.
     *
     * @param statsigProviderConfig StatsigProvider Config
     */
    public StatsigProvider(StatsigProviderConfig statsigProviderConfig) {
        this.statsigProviderConfig = statsigProviderConfig;
    }

    /**
     * Initialize the provider.
     *
     * @param evaluationContext evaluation context
     * @throws Exception on error
     */
    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        Future<Void> initFuture =
                Statsig.initializeAsync(statsigProviderConfig.getSdkKey(), statsigProviderConfig.getOptions());
        initFuture.get();

        statsigProviderConfig.postInit();
        log.info("finished initializing provider");
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    @SneakyThrows
    @Override
    @SuppressFBWarnings(
            value = {"NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"},
            justification = "reason can be null")
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        StatsigUser user = ContextTransformer.transform(ctx);
        Boolean evaluatedValue = defaultValue;
        Value featureConfigValue = ctx.getValue(FEATURE_CONFIG_KEY);
        String reason = null;
        if (featureConfigValue == null) {
            APIFeatureGate featureGate = Statsig.getFeatureGate(user, key);
            reason = featureGate.getReason().getReason();

            // in case of evaluation failure, remain with default value.
            if (!assumeFailure(featureGate)) {
                evaluatedValue = featureGate.getValue();
            }
        } else {
            FeatureConfig featureConfig = parseFeatureConfig(ctx);
            switch (featureConfig.getType()) {
                case CONFIG:
                    DynamicConfig dynamicConfig = fetchDynamicConfig(user, featureConfig);
                    evaluatedValue = dynamicConfig.getBoolean(key, defaultValue);
                    break;
                case LAYER:
                    Layer layer = fetchLayer(user, featureConfig);
                    evaluatedValue = layer.getBoolean(key, defaultValue);
                    break;
                default:
                    break;
            }
        }

        return ProviderEvaluation.<Boolean>builder()
                .value(evaluatedValue)
                .reason(reason)
                .build();
    }

    /*
    https://github.com/statsig-io/java-server-sdk/issues/22#issuecomment-2002346349
    failure is assumed by reason, since success status is not returned.
    */
    private boolean assumeFailure(APIFeatureGate featureGate) {
        EvaluationReason reason = featureGate.getReason();
        return EvaluationReason.DEFAULT.equals(reason)
                || EvaluationReason.UNINITIALIZED.equals(reason)
                || EvaluationReason.UNRECOGNIZED.equals(reason)
                || EvaluationReason.UNSUPPORTED.equals(reason);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        StatsigUser user = ContextTransformer.transform(ctx);
        FeatureConfig featureConfig = parseFeatureConfig(ctx);
        String evaluatedValue = defaultValue;
        switch (featureConfig.getType()) {
            case CONFIG:
                DynamicConfig dynamicConfig = fetchDynamicConfig(user, featureConfig);
                evaluatedValue = dynamicConfig.getString(key, defaultValue);
                break;
            case LAYER:
                Layer layer = fetchLayer(user, featureConfig);
                evaluatedValue = layer.getString(key, defaultValue);
                break;
            default:
                break;
        }
        return ProviderEvaluation.<String>builder().value(evaluatedValue).build();
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        StatsigUser user = ContextTransformer.transform(ctx);
        FeatureConfig featureConfig = parseFeatureConfig(ctx);
        Integer evaluatedValue = defaultValue;
        switch (featureConfig.getType()) {
            case CONFIG:
                DynamicConfig dynamicConfig = fetchDynamicConfig(user, featureConfig);
                evaluatedValue = dynamicConfig.getInt(key, defaultValue);
                break;
            case LAYER:
                Layer layer = fetchLayer(user, featureConfig);
                evaluatedValue = layer.getInt(key, defaultValue);
                break;
            default:
                break;
        }
        return ProviderEvaluation.<Integer>builder().value(evaluatedValue).build();
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        StatsigUser user = ContextTransformer.transform(ctx);
        FeatureConfig featureConfig = parseFeatureConfig(ctx);
        Double evaluatedValue = defaultValue;
        switch (featureConfig.getType()) {
            case CONFIG:
                DynamicConfig dynamicConfig = fetchDynamicConfig(user, featureConfig);
                evaluatedValue = dynamicConfig.getDouble(key, defaultValue);
                break;
            case LAYER:
                Layer layer = fetchLayer(user, featureConfig);
                evaluatedValue = layer.getDouble(key, defaultValue);
                break;
            default:
                break;
        }
        return ProviderEvaluation.<Double>builder().value(evaluatedValue).build();
    }

    @SneakyThrows
    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        StatsigUser user = ContextTransformer.transform(ctx);
        FeatureConfig featureConfig = parseFeatureConfig(ctx);
        Value evaluatedValue = defaultValue;
        switch (featureConfig.getType()) {
            case CONFIG:
                DynamicConfig dynamicConfig = fetchDynamicConfig(user, featureConfig);
                evaluatedValue = toValue(dynamicConfig);
                break;
            case LAYER:
                Layer layer = fetchLayer(user, featureConfig);
                evaluatedValue = toValue(layer);
                break;
            default:
                break;
        }
        return ProviderEvaluation.<Value>builder().value(evaluatedValue).build();
    }

    @SneakyThrows
    protected DynamicConfig fetchDynamicConfig(StatsigUser user, FeatureConfig featureConfig) {
        return Statsig.getConfigAsync(user, featureConfig.getName()).get();
    }

    @SneakyThrows
    protected Layer fetchLayer(StatsigUser user, FeatureConfig featureConfig) {
        return Statsig.getLayerAsync(user, featureConfig.getName()).get();
    }

    private Value toValue(DynamicConfig dynamicConfig) {
        MutableContext mutableContext = new MutableContext();
        mutableContext.add("name", dynamicConfig.getName());
        mutableContext.add("value", Structure.mapToStructure(dynamicConfig.getValue()));
        mutableContext.add("ruleID", dynamicConfig.getRuleID());
        mutableContext.add("groupName", dynamicConfig.getGroupName());
        List<Value> secondaryExposures = new ArrayList<>();
        dynamicConfig.getSecondaryExposures().forEach(secondaryExposure -> {
            Value value = Value.objectToValue(secondaryExposure);
            secondaryExposures.add(value);
        });
        mutableContext.add("secondaryExposures", secondaryExposures);
        return new Value(mutableContext);
    }

    private Value toValue(Layer layer) {
        MutableContext mutableContext = new MutableContext();
        mutableContext.add("name", layer.getName());
        mutableContext.add("value", Structure.mapToStructure(layer.getValue()));
        mutableContext.add("ruleID", layer.getRuleID());
        mutableContext.add("groupName", layer.getGroupName());
        List<Value> secondaryExposures = new ArrayList<>();
        layer.getSecondaryExposures().forEach(secondaryExposure -> {
            Value value = Value.objectToValue(secondaryExposure);
            secondaryExposures.add(value);
        });
        mutableContext.add("secondaryExposures", secondaryExposures);
        mutableContext.add("allocatedExperiment", layer.getAllocatedExperiment());
        return new Value(mutableContext);
    }

    @NotNull private static FeatureConfig parseFeatureConfig(EvaluationContext ctx) {
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
        log.info("shutdown");
        Statsig.shutdown();
    }

    /** Feature config, as required for evaluation. */
    @AllArgsConstructor
    @Getter
    public static class FeatureConfig {

        /** Type. CONFIG: Dynamic Config LAYER: Layer */
        public enum Type {
            CONFIG,
            LAYER
        }

        private Type type;
        private String name;
    }
}
