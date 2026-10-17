package dev.openfeature.contrib.tools.tck;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Whether a run drove a real backend over the control API, or a provider with no backend inside
 * this process.
 *
 * <p>Closed on purpose: an implementation that could answer anything would produce a conformance
 * claim no report can validate.
 *
 * @see BackendControl#backendType()
 */
public enum BackendType {

    /** The normative control API: a real backend driven over {@code openapi/control-api.yaml}. */
    HTTP("http"),

    /** Control of a provider with no backend (in-memory, env var, file), inside this JVM. */
    IN_PROCESS("in-process");

    private final String wireValue;

    BackendType(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the form this value takes in a conformance report.
     *
     * @return {@code http} or {@code in-process}
     */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * Returns the wire form.
     *
     * @return {@code http} or {@code in-process}
     */
    @Override
    public String toString() {
        return wireValue;
    }
}
