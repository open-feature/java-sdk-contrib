package dev.openfeature.contrib.tools.tck;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Which of the two control contracts a conformance run was conducted under.
 *
 * <p>Closed on purpose: the report schema's {@code backend.controlApi} is an enum of exactly these
 * two values, so a {@code String} here would be wider than the thing it feeds — an implementor could
 * answer {@code "HTTP"} and produce a document that fails validation with no local error.
 *
 * @see BackendControl#controlApi()
 */
public enum ControlApi {

    /**
     * The normative control API: a real backend driven over the HTTP control endpoints in
     * {@code openapi/control-api.yaml}. This is what makes a conformance claim portable.
     */
    HTTP("http"),

    /**
     * Control of a provider with no backend, exercised inside this JVM.
     *
     * <p>The narrow allowance for in-memory, environment-variable and file-based providers, where
     * "the backend" is a data structure in the same process.
     */
    IN_PROCESS("in-process");

    private final String wireValue;

    ControlApi(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the form this value takes in a conformance report and in the control API definition.
     *
     * @return {@code http} or {@code in-process}
     */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * Returns the wire form, so logs and failure messages quote the spelling a reader sees in the
     * report.
     *
     * @return {@code http} or {@code in-process}
     */
    @Override
    public String toString() {
        return wireValue;
    }
}
