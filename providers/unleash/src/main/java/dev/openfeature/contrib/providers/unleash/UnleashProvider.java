package dev.openfeature.contrib.providers.unleash;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.getunleash.Variant;
import io.getunleash.strategy.Strategy;
import io.getunleash.util.UnleashConfig;
import io.getunleash.variant.Payload;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Provider implementation for Unleash.
 */
@Slf4j
public class UnleashProvider extends EventProvider {

    @Getter
    private static final String NAME = "Unleash Provider";
    public static final String NOT_IMPLEMENTED =
        "Not implemented - provider does not support this type. Only boolean is supported.";
    public static final String CONTEXT_APP_NAME = "appName";
    public static final String CONTEXT_USER_ID = "userId";
    public static final String CONTEXT_ENVIRONMENT = "environment";
    public static final String CONTEXT_REMOTE_ADDRESS = "remoteAddress";
    public static final String CONTEXT_SESSION_ID = "sessionId";
    public static final String CONTEXT_CURRENT_TIME = "currentTime";
    public static final String PROVIDER_NOT_YET_INITIALIZED = "provider not yet initialized";
    public static final String UNKNOWN_ERROR = "unknown error";

    @Getter(AccessLevel.PROTECTED)
    private UnleashOptions unleashOptions;

    @Setter(AccessLevel.PROTECTED)
    @Getter
    private Unleash unleash;

    @Setter(AccessLevel.PROTECTED)
    @Getter
    private ProviderState state = ProviderState.NOT_READY;

    /**
     * Constructor.
     * @param unleashOptions UnleashOptions
     */
    public UnleashProvider(UnleashOptions unleashOptions) {
        this.unleashOptions = unleashOptions;
    }

    /**
     * Initialize the provider.
     * @param evaluationContext evaluation context
     * @throws Exception on error
     */
    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        super.initialize(evaluationContext);
        UnleashSubscriberWrapper unleashSubscriberWrapper = new UnleashSubscriberWrapper(
            unleashOptions.getUnleashConfigBuilder().build().getSubscriber(), this);
        unleashOptions.getUnleashConfigBuilder().subscriber(unleashSubscriberWrapper);
        UnleashConfig unleashConfig = unleashOptions.getUnleashConfigBuilder().build();
        unleash = new DefaultUnleash(unleashConfig,
            unleashOptions.getStrategyMap().values().toArray(new Strategy[unleashOptions.getStrategyMap().size()]));

        // else, state will be changed via UnleashSubscriberWrapper events
        if (unleashConfig.isSynchronousFetchOnInitialisation()) {
            state = ProviderState.READY;
        } else {
            log.info("ready state will be changed via UnleashSubscriberWrapper events");
        }

        log.info("finished initializing provider, state: {}", state);
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    @Override
    public void emitProviderReady(ProviderEventDetails details) {
        super.emitProviderReady(details);
        state = ProviderState.READY;
    }

    @Override
    public void emitProviderError(ProviderEventDetails details) {
        super.emitProviderError(details);
        state = ProviderState.ERROR;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        if (!ProviderState.READY.equals(state)) {
            if (ProviderState.NOT_READY.equals(state)) {
                throw new ProviderNotReadyError(PROVIDER_NOT_YET_INITIALIZED);
            }
            throw new GeneralError(UNKNOWN_ERROR);
        }
        UnleashContext context = ctx == null ? UnleashContext.builder().build() : transform(ctx);
        boolean featureBooleanValue = unleash.isEnabled(key, context, defaultValue);
        return ProviderEvaluation.<Boolean>builder()
            .value(featureBooleanValue)
            .reason(Reason.UNKNOWN.name())
            .build();
    }

    protected static UnleashContext transform(EvaluationContext ctx) {
        UnleashContext.Builder unleashContextBuilder = new UnleashContext.Builder();
        ctx.asObjectMap().forEach((k, v) -> {
            switch (k) {
                case CONTEXT_APP_NAME:
                    unleashContextBuilder.appName(String.valueOf(v));
                    break;
                case CONTEXT_USER_ID:
                    unleashContextBuilder.userId(String.valueOf(v));
                    break;
                case CONTEXT_ENVIRONMENT:
                    unleashContextBuilder.environment(String.valueOf(v));
                    break;
                case CONTEXT_REMOTE_ADDRESS:
                    unleashContextBuilder.remoteAddress(String.valueOf(v));
                    break;
                case CONTEXT_SESSION_ID:
                    unleashContextBuilder.sessionId(String.valueOf(v));
                    break;
                case CONTEXT_CURRENT_TIME:
                    unleashContextBuilder.currentTime(ZonedDateTime.parse(String.valueOf(v)));
                    break;
                default:
                    unleashContextBuilder.addProperty(k, String.valueOf(v));
                    break;
            }
        });
        return unleashContextBuilder.build();
    }

    /**
     * Transform UnleashContext to EvaluationContext.
     * @param unleashContext the UnleashContext
     * @return transformed EvaluationContext
     */
    public static EvaluationContext transform(UnleashContext unleashContext) {
        Map<String, Value> attributes = new HashMap<>();
        unleashContext.getAppName().ifPresent(o -> attributes.put(CONTEXT_APP_NAME, Value.objectToValue(o)));
        unleashContext.getUserId().ifPresent(o -> attributes.put(CONTEXT_USER_ID, Value.objectToValue(o)));
        unleashContext.getEnvironment().ifPresent(o -> attributes.put(CONTEXT_ENVIRONMENT, Value.objectToValue(o)));
        unleashContext.getSessionId().ifPresent(o -> attributes.put(CONTEXT_SESSION_ID, Value.objectToValue(o)));
        unleashContext.getRemoteAddress().ifPresent(o -> attributes.put(
            CONTEXT_REMOTE_ADDRESS, Value.objectToValue(o)));
        unleashContext.getCurrentTime().ifPresent(o -> attributes.put(CONTEXT_CURRENT_TIME, Value.objectToValue(o)));

        unleashContext.getProperties().forEach((k, v) -> {
            attributes.put(k, Value.objectToValue(v));
        });
        return new ImmutableContext(attributes);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        if (!ProviderState.READY.equals(state)) {
            if (ProviderState.NOT_READY.equals(state)) {
                throw new ProviderNotReadyError(PROVIDER_NOT_YET_INITIALIZED);
            }
            throw new GeneralError(UNKNOWN_ERROR);
        }
        UnleashContext context = ctx == null ? UnleashContext.builder().build() : transform(ctx);
        Payload defaultVariantPayload = new Payload("string", String.valueOf(defaultValue));
        Variant defaultVariant = new Variant("default_fallback", defaultVariantPayload, true);
        Variant evaluatedVariant = unleash.getVariant(key, context, defaultVariant);
        Payload evaluatedVariantPayload = evaluatedVariant.getPayload().orElse(defaultVariantPayload);
        String evaluatedVariantPayloadValue = evaluatedVariantPayload.getValue();
        return ProviderEvaluation.<String>builder()
            .value(evaluatedVariantPayloadValue)
            .reason(Reason.UNKNOWN.name())
            .build();
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
