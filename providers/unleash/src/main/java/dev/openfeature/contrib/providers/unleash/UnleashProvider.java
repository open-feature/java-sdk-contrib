package dev.openfeature.contrib.providers.unleash;

import static io.getunleash.Variant.DISABLED_VARIANT;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.getunleash.Variant;
import io.getunleash.util.UnleashConfig;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Provider implementation for Unleash.
 */
@Slf4j
public class UnleashProvider extends EventProvider {

    @Getter
    private static final String NAME = "Unleash";
    public static final String NOT_IMPLEMENTED =
        "Not implemented - provider does not support this type. Only boolean is supported.";

    public static final String PROVIDER_NOT_YET_INITIALIZED = "provider not yet initialized";
    public static final String UNKNOWN_ERROR = "unknown error";

    @Getter(AccessLevel.PROTECTED)
    private UnleashProviderConfig unleashProviderConfig;

    @Setter(AccessLevel.PROTECTED)
    @Getter
    private Unleash unleash;

    private AtomicBoolean isInitialized = new AtomicBoolean(false);

    /**
     * Constructor.
     * @param unleashProviderConfig UnleashProviderConfig
     */
    public UnleashProvider(UnleashProviderConfig unleashProviderConfig) {
        this.unleashProviderConfig = unleashProviderConfig;
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
        UnleashSubscriberWrapper unleashSubscriberWrapper = new UnleashSubscriberWrapper(
            unleashProviderConfig.getUnleashConfigBuilder().build().getSubscriber(), this);
        unleashProviderConfig.getUnleashConfigBuilder().subscriber(unleashSubscriberWrapper);
        UnleashConfig unleashConfig = unleashProviderConfig.getUnleashConfigBuilder().build();
        unleash = new DefaultUnleash(unleashConfig);

        // Unleash is per definition ready after it is initialized.
        log.info("finished initializing provider");
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    
    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        UnleashContext context = ctx == null ? UnleashContext.builder().build() : ContextTransformer.transform(ctx);
        boolean featureBooleanValue = unleash.isEnabled(key, context, defaultValue);
        return ProviderEvaluation.<Boolean>builder()
            .value(featureBooleanValue)
            .build();
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        ProviderEvaluation<Value> valueProviderEvaluation = getObjectEvaluation(key, new Value(defaultValue), ctx);
        return ProviderEvaluation.<String>builder()
            .value(valueProviderEvaluation.getValue().asString())
            .variant(valueProviderEvaluation.getVariant())
                .errorCode(valueProviderEvaluation.getErrorCode())
                .reason(valueProviderEvaluation.getReason())
                .flagMetadata(valueProviderEvaluation.getFlagMetadata())
            .build();
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        ProviderEvaluation<Value> valueProviderEvaluation = getObjectEvaluation(key, new Value(defaultValue), ctx);
        Integer value = getIntegerValue(valueProviderEvaluation, defaultValue);
        return ProviderEvaluation.<Integer>builder()
            .value(value)
            .variant(valueProviderEvaluation.getVariant())
            .errorCode(valueProviderEvaluation.getErrorCode())
            .reason(valueProviderEvaluation.getReason())
            .flagMetadata(valueProviderEvaluation.getFlagMetadata())
            .build();
    }

    private static Integer getIntegerValue(ProviderEvaluation<Value> valueProviderEvaluation, Integer defaultValue) {
        String valueStr = valueProviderEvaluation.getValue().asObject().toString();
        try {
            return Integer.parseInt(valueStr);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        ProviderEvaluation<Value> valueProviderEvaluation = getObjectEvaluation(key, new Value(defaultValue), ctx);
        Double value = getDoubleValue(valueProviderEvaluation, defaultValue);
        return ProviderEvaluation.<Double>builder()
            .value(value)
            .variant(valueProviderEvaluation.getVariant())
            .errorCode(valueProviderEvaluation.getErrorCode())
            .reason(valueProviderEvaluation.getReason())
            .flagMetadata(valueProviderEvaluation.getFlagMetadata())
            .build();
    }

    private static Double getDoubleValue(ProviderEvaluation<Value> valueProviderEvaluation, Double defaultValue) {
        String valueStr = valueProviderEvaluation.getValue().asObject().toString();
        try {
            return Double.parseDouble(valueStr);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        UnleashContext context = ctx == null ? UnleashContext.builder().build() : ContextTransformer.transform(ctx);
        Variant evaluatedVariant = unleash.getVariant(key, context);
        String variantName;
        Value value;
        if (DISABLED_VARIANT.equals(evaluatedVariant)) {
            variantName = null;
            value = defaultValue;
        } else {
            variantName = evaluatedVariant.getName();
            value = evaluatedVariant.getPayload().map(p -> new Value(p.getValue())).orElse(null);
        }
        ImmutableMetadata.ImmutableMetadataBuilder flagMetadataBuilder = ImmutableMetadata.builder()
            .addString("variant-stickiness", evaluatedVariant.getStickiness());
        flagMetadataBuilder.addBoolean("enabled", evaluatedVariant.isEnabled());
        if (evaluatedVariant.getPayload().isPresent()) {
            flagMetadataBuilder.addString("payload-type", evaluatedVariant.getPayload().get().getType());
        }
        return ProviderEvaluation.<Value>builder()
            .value(value)
            .variant(variantName)
            .flagMetadata(flagMetadataBuilder.build())
            .build();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        log.info("shutdown");
        if (unleash != null) {
            unleash.shutdown();
        }
    }
}
