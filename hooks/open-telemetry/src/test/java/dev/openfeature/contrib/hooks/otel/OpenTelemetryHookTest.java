package dev.openfeature.contrib.hooks.otel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenTelemetryHookTest {

    @Test
    @DisplayName("a simple test!")
    void test() {
        assertThat(OpenTelemetryHook.test()).isEqualTo(true);
    }
}
