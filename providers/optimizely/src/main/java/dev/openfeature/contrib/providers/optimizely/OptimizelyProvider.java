package dev.openfeature.contrib.providers.optimizely;

import com.optimizely.ab.Optimizely;
import com.optimizely.ab.OptimizelyUserContext;
import com.optimizely.ab.optimizelydecision.OptimizelyDecision;
import com.optimizely.ab.optimizelyjson.OptimizelyJSON;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import java.util.List;
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
        OptimizelyDecision decision = userContext.decide(key);
        String variationKey = decision.getVariationKey();
        String reasonsString = null;
        if (variationKey == null) {
            List<String> reasons = decision.getReasons();
            reasonsString = String.join(", ", reasons);
        }

        boolean enabled = decision.getEnabled();
        return ProviderEvaluation.<Boolean>builder()
                .value(enabled)
                .reason(reasonsString)
                .build();
    }

    @SneakyThrows
    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        throw new UnsupportedOperationException("String evaluation is not directly supported by Optimizely provider,"
                + "use getObjectEvaluation instead.");
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        throw new UnsupportedOperationException("Integer evaluation is not directly supported by Optimizely provider,"
                + "use getObjectEvaluation instead.");
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        throw new UnsupportedOperationException("Double evaluation is not directly supported by Optimizely provider,"
                + "use getObjectEvaluation instead.");
    }

    @SneakyThrows
    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        OptimizelyUserContext userContext = contextTransformer.transform(ctx);
        OptimizelyDecision decision = userContext.decide(key);
        String variationKey = decision.getVariationKey();
        String reasonsString = null;
        if (variationKey == null) {
            List<String> reasons = decision.getReasons();
            reasonsString = String.join(", ", reasons);
        }

        Value evaluatedValue = defaultValue;
        boolean enabled = decision.getEnabled();
        if (enabled) {
            OptimizelyJSON variables = decision.getVariables();
            evaluatedValue = toValue(variables);
        }

        return ProviderEvaluation.<Value>builder()
                .value(evaluatedValue)
                .reason(reasonsString)
                .variant(variationKey)
                .build();
    }

    @SneakyThrows
    private Value toValue(OptimizelyJSON optimizelyJson) {
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
