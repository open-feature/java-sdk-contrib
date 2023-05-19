package dev.openfeature.contrib.hooks.otel;

import lombok.Builder;
import lombok.Getter;

/**
 * OpenTelemetry hook options.
 */
@Builder
@Getter
public class OpenTelemetryHookOptions {

    /**
     * Control Span error status. Default is false - Span status is unchanged for hook error
     */
    @Builder.Default
    private boolean setSpanErrorStatus = false;
}
