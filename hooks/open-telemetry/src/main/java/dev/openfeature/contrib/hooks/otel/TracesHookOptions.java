package dev.openfeature.contrib.hooks.otel;

import dev.openfeature.sdk.ImmutableMetadata;
import io.opentelemetry.api.common.Attributes;
import lombok.Builder;
import lombok.Getter;

import java.util.function.Function;

/**
 * OpenTelemetry {@link TracesHook} options.
 */
@Builder
@Getter
public class TracesHookOptions {
    /**
     * Control Span error status. Default is false - Span status is unchanged for hook error
     */
    @Builder.Default
    private boolean setSpanErrorStatus = false;

    /**
     * Custom callback to derive {@link Attributes} from flag evaluation metadata represented with
     * {@link ImmutableMetadata}.
     */
    private Function<ImmutableMetadata, Attributes> dimensionExtractor;

    /**
     * Extra attributes added at hook constructor time.
     */
    private Attributes extraAttributes;
}
