package dev.openfeature.contrib.hooks.otel;

import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

import java.util.Map;

/**
 * The OpenTelemetry hook provides a way to automatically add a feature flag evaluation to a span as a span event.
 * Refer to <a href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/feature-flags.md">OpenTelemetry</a>
 */
public class OpenTelemetryHook implements Hook {

    private static final String EVENT_NAME = "feature_flag";

    private final boolean setSpanErrorStatus;

    private final AttributeKey<String> flagKeyAttributeKey = AttributeKey.stringKey(EVENT_NAME + ".flag_key");
    private final AttributeKey<String> providerNameAttributeKey = AttributeKey.stringKey(EVENT_NAME + ".provider_name");
    private final AttributeKey<String> variantAttributeKey = AttributeKey.stringKey(EVENT_NAME + ".variant");

    /**
     * Create a new OpenTelemetryHook instance with default options.
     */
    public OpenTelemetryHook() {
        this(OpenTelemetryHookOptions.builder().build());
    }

    /**
     * Create a new OpenTelemetryHook instance with options.
     */
    public OpenTelemetryHook(OpenTelemetryHookOptions options) {
        setSpanErrorStatus = options.isSetSpanErrorStatus();
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
    @Override public void after(HookContext ctx, FlagEvaluationDetails details, Map hints) {
        Span currentSpan = Span.current();
        if (currentSpan == null) {
            return;
        }

        String variant = details.getVariant() != null ? details.getVariant() : String.valueOf(details.getValue());
        Attributes attributes = Attributes.of(flagKeyAttributeKey, ctx.getFlagKey(), providerNameAttributeKey,
                ctx.getProviderMetadata().getName(), variantAttributeKey, variant);
        currentSpan.addEvent(EVENT_NAME, attributes);
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
    @Override public void error(HookContext ctx, Exception error, Map hints) {
        Span currentSpan = Span.current();
        if (currentSpan == null) {
            return;
        }

        if (setSpanErrorStatus) {
            currentSpan.setStatus(StatusCode.ERROR);
        }

        Attributes attributes = Attributes.of(flagKeyAttributeKey, ctx.getFlagKey(), providerNameAttributeKey,
                ctx.getProviderMetadata().getName());
        currentSpan.recordException(error, attributes);
    }
}
