package dev.openfeature.contrib.hooks.otel;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.ImmutableMetadata;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static dev.openfeature.contrib.hooks.otel.OTelCommons.ERROR_KEY;
import static dev.openfeature.contrib.hooks.otel.OTelCommons.REASON_KEY;
import static dev.openfeature.contrib.hooks.otel.OTelCommons.flagKeyAttributeKey;
import static dev.openfeature.contrib.hooks.otel.OTelCommons.providerNameAttributeKey;
import static dev.openfeature.contrib.hooks.otel.OTelCommons.variantAttributeKey;

/**
 * OpenTelemetry metric hook records metrics at different {@link Hook} stages.
 */
@Slf4j
@SuppressWarnings("PMD.TooManyStaticImports")
public class MetricsHook implements Hook {

    private static final String METER_NAME = "java.openfeature.dev";
    private static final String EVALUATION_ACTIVE_COUNT = "feature_flag.evaluation_active_count";
    private static final String EVALUATION_REQUESTS_TOTAL = "feature_flag.evaluation_requests_total";
    private static final String FLAG_EVALUATION_SUCCESS_TOTAL = "feature_flag.evaluation_success_total";
    private static final String FLAG_EVALUATION_ERROR_TOTAL = "feature_flag.evaluation_error_total";

    private final LongUpDownCounter activeFlagEvaluationsCounter;
    private final LongCounter evaluationRequestCounter;
    private final LongCounter evaluationSuccessCounter;
    private final LongCounter evaluationErrorCounter;
    private final List<DimensionDescription> dimensionDescriptions;
    private final Function<ImmutableMetadata, Attributes> extractor;

    /**
     * Construct a metric hook by providing an {@link OpenTelemetry} instance.
     */
    public MetricsHook(final OpenTelemetry openTelemetry) {
        this(openTelemetry, Collections.emptyList());
    }

    /**
     * Construct a metric hook with {@link OpenTelemetry} instance and a list of {@link DimensionDescription}.
     * Provided dimensions are attempted to be extracted from ImmutableMetadata attached to
     * {@link FlagEvaluationDetails}.
     *
     * @deprecated - This constructor is deprecated. Please use {@link MetricHookOptions} based options
     */
    @Deprecated
    public MetricsHook(final OpenTelemetry openTelemetry, final List<DimensionDescription> dimensions) {
        this(openTelemetry, MetricHookOptions.builder().setDimensions(dimensions).build());
    }

    /**
     * Construct a metric hook with {@link OpenTelemetry} instance and options for the hook.
     */
    public MetricsHook(final OpenTelemetry openTelemetry, final MetricHookOptions options) {
        final Meter meter = openTelemetry.getMeter(METER_NAME);

        activeFlagEvaluationsCounter =
                meter.upDownCounterBuilder(EVALUATION_ACTIVE_COUNT).setDescription("active flag evaluations counter")
                        .build();

        evaluationRequestCounter = meter.counterBuilder(EVALUATION_REQUESTS_TOTAL)
                .setDescription("feature flag evaluation request counter")
                .setUnit("{request}")
                .build();

        evaluationSuccessCounter = meter.counterBuilder(FLAG_EVALUATION_SUCCESS_TOTAL)
                .setDescription("feature flag evaluation success counter")
                .setUnit("{impression}")
                .build();

        evaluationErrorCounter = meter.counterBuilder(FLAG_EVALUATION_ERROR_TOTAL)
                .setDescription("feature flag evaluation error counter")
                .build();

        dimensionDescriptions = Collections.unmodifiableList(options.getSetDimensions());
        extractor = options.getAttributeSetter();
    }


    @Override
    public Optional<EvaluationContext> before(HookContext ctx, Map hints) {
        activeFlagEvaluationsCounter.add(+1, Attributes.of(flagKeyAttributeKey, ctx.getFlagKey()));

        evaluationRequestCounter.add(+1, Attributes.of(flagKeyAttributeKey, ctx.getFlagKey(), providerNameAttributeKey,
                ctx.getProviderMetadata().getName()));
        return Optional.empty();
    }

    @Override
    public void after(HookContext ctx, FlagEvaluationDetails details, Map hints) {
        final AttributesBuilder attributesBuilder = Attributes.builder();

        attributesBuilder.put(flagKeyAttributeKey, ctx.getFlagKey());
        attributesBuilder.put(providerNameAttributeKey, ctx.getProviderMetadata().getName());

        if (details.getReason() != null) {
            attributesBuilder.put(REASON_KEY, details.getReason());
        }

        if (details.getVariant() != null) {
            attributesBuilder.put(variantAttributeKey, details.getVariant());
        } else {
            attributesBuilder.put(variantAttributeKey, String.valueOf(details.getValue()));
        }

        if (!dimensionDescriptions.isEmpty()) {
            attributesBuilder.putAll(attributesFromFlagMetadata(details.getFlagMetadata(), dimensionDescriptions));
        }

        if (extractor != null) {
            attributesBuilder.putAll(extractor.apply(details.getFlagMetadata()));
        }

        evaluationSuccessCounter.add(+1, attributesBuilder.build());
    }

    @Override
    public void error(HookContext ctx, Exception error, Map hints) {
        final AttributesBuilder attributesBuilder = Attributes.builder();

        attributesBuilder.put(flagKeyAttributeKey, ctx.getFlagKey());
        attributesBuilder.put(providerNameAttributeKey, ctx.getProviderMetadata().getName());

        if (error.getMessage() != null) {
            attributesBuilder.put(ERROR_KEY, error.getMessage());
        }

        evaluationErrorCounter.add(+1, attributesBuilder.build());
    }

    @Override
    public void finallyAfter(HookContext ctx, Map hints) {
        activeFlagEvaluationsCounter.add(-1, Attributes.of(flagKeyAttributeKey, ctx.getFlagKey()));
    }

    /**
     * A helper to derive attributes from {@link DimensionDescription}.
     */
    private static Attributes attributesFromFlagMetadata(final ImmutableMetadata flagMetadata,
                                                         final List<DimensionDescription> dimensionDescriptions) {
        final AttributesBuilder builder = Attributes.builder();

        for (DimensionDescription dimension : dimensionDescriptions) {
            final Object value = flagMetadata.getValue(dimension.getKey(), dimension.getType());

            if (value == null) {
                log.debug("No value mapping found for key " + dimension.getKey() + " of type "
                        + dimension.getType().getSimpleName());
                continue;
            }

            if (dimension.getType().equals(String.class)) {
                builder.put(dimension.getKey(), (String) value);
            } else if (dimension.getType().equals(Integer.class)) {
                builder.put(dimension.getKey(), (Integer) value);
            }
            if (dimension.getType().equals(Long.class)) {
                builder.put(dimension.getKey(), (Long) value);
            }
            if (dimension.getType().equals(Float.class)) {
                builder.put(dimension.getKey(), (Float) value);
            }
            if (dimension.getType().equals(Double.class)) {
                builder.put(dimension.getKey(), (Double) value);
            }
            if (dimension.getType().equals(Boolean.class)) {
                builder.put(dimension.getKey(), (Boolean) value);
            }
        }

        return builder.build();
    }
}
