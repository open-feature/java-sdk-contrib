package dev.openfeature.contrib.tools.tck;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What kind of backend a run drove.
 *
 * @see BackendControl#backendType()
 */
public enum BackendType {

    /** The normative control API: a real backend driven over {@code openapi/control-api.yaml}. */
    HTTP("http"),

    /** Control of a provider with no backend (in-memory, env var, file), inside this JVM. */
    IN_PROCESS("in-process"),

    /** A control the TCK does not ship; the default for custom {@link BackendControl}s. */
    CUSTOM("custom");

    private final String wireValue;

    BackendType(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the form this value takes in a conformance report.
     *
     * @return {@code http}, {@code in-process} or {@code custom}
     */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * Returns the wire form.
     *
     * @return {@code http}, {@code in-process} or {@code custom}
     */
    @Override
    public String toString() {
        return wireValue;
    }
}
