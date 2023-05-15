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
     * Control Span error status. Default is true - Span status is set to Error if an error occurs.
     */
    @Builder.Default
    private boolean setErrorStatus = true;
}
