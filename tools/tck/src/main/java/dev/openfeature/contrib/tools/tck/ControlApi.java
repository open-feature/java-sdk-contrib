package dev.openfeature.contrib.tools.tck;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Which of the two control contracts a conformance run was conducted under; mirrors the report
 * schema's {@code backend.controlApi} enum.
 *
 * @see BackendControl#controlApi()
 */
public enum ControlApi {

    /** The normative control API: a real backend driven over {@code openapi/control-api.yaml}. */
    HTTP("http"),

    /** Control of a provider with no backend (in-memory, env var, file), inside this JVM. */
    IN_PROCESS("in-process");

    private final String wireValue;

    ControlApi(String wireValue) {
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
