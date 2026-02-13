package dev.openfeature.contrib.providers.optimizely;

import com.optimizely.ab.Optimizely;
import com.optimizely.ab.OptimizelyUserContext;
import com.optimizely.ab.optimizelyjson.OptimizelyJSON;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import java.util.Map;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/** Provider implementation for Optimizely. */
@Slf4j
public class OptimizelyProvider extends EventProvider {

    @Getter
    private static final String NAME = "Optimizely";

    private OptimizelyProviderConfig optimizelyProviderConfig;

    @Getter
    private Optimizely optimizely;

    private ContextTransformer contextTransformer;

    /**
     * Constructor.
     *
     * @param optimizelyProviderConfig configuration for the provider
     */
    public OptimizelyProvider(OptimizelyProviderConfig optimizelyProviderConfig) {
        this.optimizelyProviderConfig = optimizelyProviderConfig;
    }

    /**
     * Initialize the provider.
     *
     * @param evaluationContext evaluation context
     * @throws Exception on error
     */
    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        optimizely = Optimizely.builder()
                .withConfigManager(optimizelyProviderConfig.getProjectConfigManager())
                .withEventProcessor(optimizelyProviderConfig.getEventProcessor())
                .withDatafile(optimizelyProviderConfig.getDatafile())
                .withDefaultDecideOptions(optimizelyProviderConfig.getDefaultDecideOptions())
                .withErrorHandler(optimizelyProviderConfig.getErrorHandler())
                .withODPManager(optimizelyProviderConfig.getOdpManager())
                .withUserProfileService(optimizelyProviderConfig.getUserProfileService())
                .build();
        contextTransformer = ContextTransformer.builder().optimizely(optimizely).build();
        log.info("finished initializing provider");
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    @SneakyThrows
    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        OptimizelyUserContext userContext = contextTransformer.transform(ctx);

        String variableKey = getVariableKey(ctx);
        Boolean enabled = optimizely.getFeatureVariableBoolean(
                key, variableKey, userContext.getUserId(), userContext.getAttributes());

        String variant = variableKey;
        String reason = Reason.TARGETING_MATCH.name();
        if (enabled == null) {
            enabled = false;
            variant = null;
            reason = Reason.DEFAULT.name();
        }

        return ProviderEvaluation.<Boolean>builder()
                .value(enabled)
                .reason(reason)
                .variant(variant)
                .build();
    }

    private static String getVariableKey(EvaluationContext ctx) {
        String variableKey = "value";
        Value varKey = ctx.getValue("variableKey");
        if (varKey != null && varKey.isString() && !varKey.asString().isBlank()) {
            variableKey = varKey.asString();
        }
        return variableKey;
    }

    @SneakyThrows
    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        OptimizelyUserContext userContext = contextTransformer.transform(ctx);

        String variableKey = getVariableKey(ctx);
        String value = optimizely.getFeatureVariableString(
                key, variableKey, userContext.getUserId(), userContext.getAttributes());

        String variant = variableKey;
        String reason = Reason.TARGETING_MATCH.name();
        if (value == null) {
            value = defaultValue;
            variant = null;
            reason = Reason.DEFAULT.name();
        }

        return ProviderEvaluation.<String>builder()
                .value(value)
                .reason(reason)
                .variant(variant)
                .build();
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        OptimizelyUserContext userContext = contextTransformer.transform(ctx);

        String variableKey = getVariableKey(ctx);
        Integer value = optimizely.getFeatureVariableInteger(
                key, variableKey, userContext.getUserId(), userContext.getAttributes());

        String variant = variableKey;
        String reason = Reason.TARGETING_MATCH.name();
        if (value == null) {
            value = defaultValue;
            variant = null;
            reason = Reason.DEFAULT.name();
        }

        return ProviderEvaluation.<Integer>builder()
                .value(value)
                .reason(reason)
                .variant(variant)
                .build();
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        OptimizelyUserContext userContext = contextTransformer.transform(ctx);

        String variableKey = getVariableKey(ctx);
        Double value = optimizely.getFeatureVariableDouble(
                key, variableKey, userContext.getUserId(), userContext.getAttributes());

        String variant = variableKey;
        String reason = Reason.TARGETING_MATCH.name();
        if (value == null) {
            value = defaultValue;
            variant = null;
            reason = Reason.DEFAULT.name();
        }

        return ProviderEvaluation.<Double>builder()
                .value(value)
                .reason(reason)
                .variant(variant)
                .build();
    }

    @SneakyThrows
    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        OptimizelyUserContext userContext = contextTransformer.transform(ctx);

        String variableKey = getVariableKey(ctx);
        OptimizelyJSON value = optimizely.getFeatureVariableJSON(
                key, variableKey, userContext.getUserId(), userContext.getAttributes());
        Value evaluatedValue = toValue(value);

        String variant = variableKey;
        String reason = Reason.TARGETING_MATCH.name();
        if (value == null) {
            evaluatedValue = defaultValue;
            variant = null;
            reason = Reason.DEFAULT.name();
        }

        return ProviderEvaluation.<Value>builder()
                .value(evaluatedValue)
                .reason(reason)
                .variant(variant)
                .build();
    }

    @SneakyThrows
    private Value toValue(OptimizelyJSON optimizelyJson) {
        if (optimizelyJson == null) {
            return new Value();
        }
        Map<String, Object> map = optimizelyJson.toMap();
        Structure structure = Structure.mapToStructure(map);
        return new Value(structure);
    }

    @SneakyThrows
    @Override
    public void shutdown() {
        log.info("shutdown");
        if (optimizely != null) {
            optimizely.close();
        }
    }
}
