package dev.openfeature.contrib.tools.tck;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What kind of backend a run drove: one reached over the control API, a provider with no backend
 * inside this process, or a control the TCK does not ship.
 *
 * <p>Closed on purpose. A {@code String} here would be wider than the vocabulary a report can
 * compare, and {@link #CUSTOM} already covers the case a third value would be invented for.
 *
 * @see BackendControl#backendType()
 */
public enum BackendType {

    /** The normative control API: a real backend driven over {@code openapi/control-api.yaml}. */
    HTTP("http"),

    /** Control of a provider with no backend (in-memory, env var, file), inside this JVM. */
    IN_PROCESS("in-process"),

    /**
     * A control the TCK does not ship, so the run is not directly comparable with the two above.
     *
     * <p>The default for an implementation of {@link BackendControl} that does not say otherwise.
     * One that genuinely drives {@code openapi/control-api.yaml} should return {@link #HTTP}
     * instead, and claim the comparability that goes with it.
     */
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
