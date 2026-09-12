package dev.openfeature.contrib.tools.tck;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Which of the two control contracts a conformance run was conducted under.
 *
 * <p>Closed on purpose. The report schema's {@code backend.controlApi} is an enum of exactly these
 * two values, and a {@code String} here would be wider than the thing it feeds: an implementor could
 * answer {@code "HTTP"} and produce a document that fails validation with no local error. There is
 * also no third case to leave room for — every {@link BackendControl} is either driving a real
 * backend over the normative HTTP endpoints or manipulating an in-process one.
 *
 * <p>{@link BackendControl#controlApi()} has no default for the same reason. The value answers a
 * question only the author of a control can answer, it cannot be inferred from the control's
 * concrete type once an adopter writes a custom one, and the two runs it distinguishes are not the
 * same claim: the same scenarios passing over the control API and passing through in-process
 * manipulation of a provider that does have a backend prove different things, and this is the only
 * field that separates them.
 *
 * @see BackendControl#controlApi()
 */
public enum ControlApi {

    /**
     * The normative control API: a real backend driven over the HTTP control endpoints in
     * {@code openapi/control-api.yaml}.
     *
     * <p>This is what makes a conformance claim portable — another language's TCK drives the same
     * endpoints against the same stack and must get the same answers.
     */
    HTTP("http"),

    /**
     * Control of a provider with no backend, exercised inside this JVM.
     *
     * <p>The narrow allowance for in-memory, environment-variable and file-based providers, where
     * "the backend" is a data structure in the same process. A claim of {@code in-process} for a
     * provider that does have a backend should be treated with suspicion.
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
     * Returns the wire form, so that logs and failure messages quote the spelling a reader will see
     * in the report rather than the enum constant.
     *
     * @return {@code http} or {@code in-process}
     */
    @Override
    public String toString() {
        return wireValue;
    }
}
