package dev.openfeature.contrib.hooks.otel;

import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.ImmutableMetadata;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

import java.util.Map;
import java.util.function.Function;

import static dev.openfeature.contrib.hooks.otel.OTelCommons.EVENT_NAME;
import static dev.openfeature.contrib.hooks.otel.OTelCommons.flagKeyAttributeKey;
import static dev.openfeature.contrib.hooks.otel.OTelCommons.providerNameAttributeKey;
import static dev.openfeature.contrib.hooks.otel.OTelCommons.variantAttributeKey;

/**
 * The OpenTelemetry hook provides a way to automatically add a feature flag evaluation to a span as a span event.
 * Refer to <a href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/feature-flags.md">OpenTelemetry</a>
 */
public class TracesHook implements Hook {
    private final boolean setSpanErrorStatus;
    private final Function<ImmutableMetadata, Attributes> extractor;
    private final Attributes extraAttributes;

    /**
     * Create a new OpenTelemetryHook instance with default options.
     */
    public TracesHook() {
        this(TracesHookOptions.builder().build());
    }

    /**
     * Create a new OpenTelemetryHook instance with options.
     */
    public TracesHook(TracesHookOptions options) {
        setSpanErrorStatus = options.isSetSpanErrorStatus();
        extractor = options.getDimensionExtractor();
        extraAttributes = options.getExtraAttributes();
    }

    /**
     * Records the event in the current span after the successful flag evaluation. Span is derived from the current
     * context. Refer OpenTelemetry documentation on nested spans for more details -
     * <a href="https://opentelemetry.io/docs/instrumentation/java/manual/#create-nested-spans">Nested Spans</a>
     *
     * @param ctx     Information about the particular flag evaluation
     * @param details Information about how the flag was resolved, including any resolved values.
     * @param hints   An immutable mapping of data for users to communicate to the hooks.
     */
    @Override
    public void after(HookContext ctx, FlagEvaluationDetails details, Map hints) {
        Span currentSpan = Span.current();
        if (currentSpan == null) {
            return;
        }

        String variant = details.getVariant() != null ? details.getVariant() : String.valueOf(details.getValue());

        final AttributesBuilder attributesBuilder = Attributes.builder();

        attributesBuilder.put(flagKeyAttributeKey, ctx.getFlagKey());
        attributesBuilder.put(providerNameAttributeKey, ctx.getProviderMetadata().getName());
        attributesBuilder.put(variantAttributeKey, variant);
        attributesBuilder.putAll(extraAttributes);

        if (extractor != null) {
            attributesBuilder.putAll(extractor.apply(details.getFlagMetadata()));
        }

        currentSpan.addEvent(EVENT_NAME, attributesBuilder.build());
    }

    /**
     * Records the error details in the current span after the flag evaluation has processed abnormally. Span is derived
     * from the current context. Refer OpenTelemetry documentation on nested spans for more details -
     * <a href="https://opentelemetry.io/docs/instrumentation/java/manual/#create-nested-spans">Nested Spans</a>
     *
     * @param ctx   Information about the particular flag evaluation
     * @param error The exception that was thrown.
     * @param hints An immutable mapping of data for users to communicate to the hooks.
     */
    @Override
    public void error(HookContext ctx, Exception error, Map hints) {
        Span currentSpan = Span.current();
        if (currentSpan == null) {
            return;
        }

        if (setSpanErrorStatus) {
            currentSpan.setStatus(StatusCode.ERROR);
        }

        Attributes attributes = Attributes.builder()
                .put(flagKeyAttributeKey, ctx.getFlagKey())
                .put(providerNameAttributeKey, ctx.getProviderMetadata().getName())
                .putAll(extraAttributes)
                .build();

        currentSpan.recordException(error, attributes);
    }
}
