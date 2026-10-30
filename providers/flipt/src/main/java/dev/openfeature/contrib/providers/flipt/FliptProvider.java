package dev.openfeature.contrib.providers.flipt;

import static dev.openfeature.sdk.Reason.DEFAULT;
import static dev.openfeature.sdk.Reason.DISABLED;
import static dev.openfeature.sdk.Reason.STATIC;
import static dev.openfeature.sdk.Reason.TARGETING_MATCH;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.InvalidContextError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import io.flipt.api.FliptClient;
import io.flipt.api.error.Error;
import io.flipt.api.evaluation.models.BooleanEvaluationResponse;
import io.flipt.api.evaluation.models.EvaluationReason;
import io.flipt.api.evaluation.models.EvaluationRequest;
import io.flipt.api.evaluation.models.VariantEvaluationResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/** Provider implementation for Flipt. */
@Slf4j
public class FliptProvider extends EventProvider {

    @Getter
    private static final String NAME = "Flipt";

    public static final String PROVIDER_NOT_YET_INITIALIZED = "provider not yet initialized";
    public static final String UNKNOWN_ERROR = "unknown error";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Maps a Flipt variant response onto the value the caller asked for. */
    @FunctionalInterface
    private interface VariantMapper<T> {
        T map(VariantEvaluationResponse response, T defaultValue);
    }

    @Getter(AccessLevel.PROTECTED)
    private FliptProviderConfig fliptProviderConfig;

    @Setter(AccessLevel.PROTECTED)
    @Getter
    private FliptClient fliptClient;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    /**
     * Constructor.
     *
     * @param fliptProviderConfig FliptProviderConfig
     */
    public FliptProvider(FliptProviderConfig fliptProviderConfig) {
        this.fliptProviderConfig = fliptProviderConfig;
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
        fliptClient = fliptProviderConfig.getFliptClientBuilder().build();

        log.info("finished initializing provider");
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        BooleanEvaluationResponse response;
        try {
            response = fliptClient.evaluation().evaluateBoolean(requestFor(key, ctx));
        } catch (Exception e) {
            log.debug("Error evaluating boolean flag {}", key, e);
            throw toOpenFeatureException(e);
        }

        return ProviderEvaluation.<Boolean>builder()
                .value(response.isEnabled())
                .reason(booleanReason(response))
                .build();
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return evaluateVariant(key, defaultValue, ctx, (response, fallback) -> response.getVariantKey());
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return evaluateVariant(key, defaultValue, ctx, (response, fallback) -> parseInteger(response.getVariantKey()));
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return evaluateVariant(key, defaultValue, ctx, (response, fallback) -> parseDouble(response.getVariantKey()));
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return evaluateVariant(key, defaultValue, ctx, FliptProvider::parseObject);
    }

    /**
     * Evaluates a Flipt variant flag and maps it onto the requested type.
     *
     * <p>Flipt reports a disabled flag and a flag without a default variant as reasons rather than
     * as errors. Both resolve to the caller's default. Every other response is transformed by
     * {@code mapper}, which reports a type mismatch by throwing {@link TypeMismatchError}.
     */
    private <T> ProviderEvaluation<T> evaluateVariant(
            String key, T defaultValue, EvaluationContext ctx, VariantMapper<T> mapper) {
        VariantEvaluationResponse response;
        try {
            response = fliptClient.evaluation().evaluateVariant(requestFor(key, ctx));
        } catch (Exception e) {
            log.debug("Error evaluating variant flag {}", key, e);
            throw toOpenFeatureException(e);
        }

        if (response.getReason() == EvaluationReason.FLAG_DISABLED_EVALUATION_REASON) {
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason(DISABLED.name())
                    .build();
        }

        if (response.getReason() != EvaluationReason.MATCH_EVALUATION_REASON
                && response.getReason() != EvaluationReason.DEFAULT_EVALUATION_REASON
                && !response.isMatch()) {
            // Flipt answers UNKNOWN when no default variant is defined. Report the fallback
            // DEFAULT reason and the caller's default, not a type mismatch on the empty key.
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason(DEFAULT.name())
                    .build();
        }

        T value = mapper.map(response, defaultValue);

        return ProviderEvaluation.<T>builder()
                .value(value)
                .variant(response.getVariantKey())
                .reason(variantReason(response))
                .flagMetadata(metadataFor(response))
                .build();
    }

    private EvaluationRequest requestFor(String key, EvaluationContext ctx) {
        return EvaluationRequest.builder()
                .namespaceKey(fliptProviderConfig.getNamespace())
                .flagKey(key)
                .entityId(ctx.getTargetingKey())
                .context(ContextTransformer.transform(ctx))
                .build();
    }

    private static String booleanReason(BooleanEvaluationResponse response) {
        if (response.getReason() == EvaluationReason.FLAG_DISABLED_EVALUATION_REASON) {
            return DISABLED.name();
        }
        if (response.getReason() == EvaluationReason.MATCH_EVALUATION_REASON) {
            List<String> segmentKeys = response.getSegmentKeys();
            if (segmentKeys != null && !segmentKeys.isEmpty()) {
                return TARGETING_MATCH.name();
            }
        }
        return STATIC.name();
    }

    private static String variantReason(VariantEvaluationResponse response) {
        if (response.getReason() == EvaluationReason.MATCH_EVALUATION_REASON) {
            return TARGETING_MATCH.name();
        }
        return STATIC.name();
    }

    private static ImmutableMetadata metadataFor(VariantEvaluationResponse response) {
        ImmutableMetadata.ImmutableMetadataBuilder builder = ImmutableMetadata.builder();
        String attachment = response.getVariantAttachment();
        if (attachment != null && !attachment.isEmpty() && !"null".equals(attachment)) {
            builder.addString("variant-attachment", attachment);
        }
        return builder.build();
    }

    /**
     * Parses a Flipt variant key as an integer.
     *
     * <p>An integer string is parsed exactly. A float-formatted key that is integral, such as
     * {@code "10.0"}, narrows without loss. A fractional key such as {@code "0.5"} is a type
     * mismatch rather than a silent rounding.
     */
    private static Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) {
            throw new TypeMismatchError("value is not an integer");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            // Fall through to the lossless float form.
        }
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new TypeMismatchError("value is not an integer");
        }
        if (!Double.isFinite(parsed)
                || parsed != Math.rint(parsed)
                || parsed < Integer.MIN_VALUE
                || parsed > Integer.MAX_VALUE) {
            throw new TypeMismatchError("value is not an integer");
        }
        return (int) parsed;
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isEmpty()) {
            throw new TypeMismatchError("value is not a float");
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw new TypeMismatchError("value is not a float");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new TypeMismatchError("value is not a float");
        }
    }

    private static Value parseObject(VariantEvaluationResponse response, Value defaultValue) {
        String attachment = response.getVariantAttachment();
        if (attachment == null || attachment.isEmpty() || "null".equals(attachment)) {
            return defaultValue;
        }
        try {
            Object parsed = OBJECT_MAPPER.readValue(attachment, Object.class);
            if (parsed == null) {
                return defaultValue;
            }
            return Value.objectToValue(parsed);
        } catch (JsonProcessingException e) {
            throw new TypeMismatchError("value is not an object: " + attachment);
        }
    }

    /**
     * Translates a Flipt evaluation failure into the OpenFeature error the SDK reports.
     *
     * <p>Flipt puts the gRPC status code in the JSON error body: 5 is NOT_FOUND, 3 is
     * INVALID_ARGUMENT and 14 is UNAVAILABLE. The Java client wraps that body as the cause of an
     * {@link io.flipt.api.evaluation.EvaluationException}.
     */
    private static RuntimeException toOpenFeatureException(Exception e) {
        Throwable cause = e.getCause();
        if (cause instanceof Error) {
            Error fliptError = (Error) cause;
            String message = fliptError.getMessage();
            switch (fliptError.getCode()) {
                case 5:
                    return new FlagNotFoundError(message);
                case 3:
                    if (message != null && message.contains("flag type") && message.contains("invalid")) {
                        return new TypeMismatchError(message);
                    }
                    return new InvalidContextError(message);
                case 14:
                    return new ProviderNotReadyError(message);
                default:
                    return new GeneralError(message);
            }
        }
        return new GeneralError(e.getMessage());
    }

    @Override
    public void shutdown() {
        super.shutdown();
        log.info("shutdown");
    }
}
