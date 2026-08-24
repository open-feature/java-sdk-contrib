package dev.openfeature.contrib.tools.providertck;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The result of one scenario, or of one capability, in a conformance report.
 *
 * <p>There are four rather than two because "did not run" is not one thing. A capability the
 * provider chose not to declare is a different statement from one the language makes impossible —
 * {@code @strict-numeric-typing} cannot hold in a language with no integer type — and reporting both
 * as "not declared" would show a whole language as missing something none of its providers can have.
 *
 * <p>The wire values are fixed by the report schema in the OpenFeature specification and are
 * identical in every language's TCK.
 */
public enum Outcome {

    /** The scenario ran and every step passed. */
    PASSED("passed"),

    /** The scenario ran and a step failed. */
    FAILED("failed"),

    /**
     * The scenario was not run because the provider did not declare the capability it needs.
     *
     * <p>Always carries a reason. A skipped scenario reported without one tells a reader that
     * something was not checked but not what, which is barely better than omitting it.
     */
    NOT_DECLARED("not-declared"),

    /**
     * The scenario cannot apply to this provider, because the language makes it unsatisfiable.
     *
     * <p>Distinct from {@link #NOT_DECLARED} on purpose: nothing the provider author can do would
     * change it, so a comparison page should not display it as a gap.
     */
    NOT_APPLICABLE("not-applicable");

    private final String value;

    Outcome(String value) {
        this.value = value;
    }

    /**
     * Returns the value this outcome is written as in a report.
     *
     * @return the wire value defined by the report schema
     */
    @JsonValue
    public String value() {
        return value;
    }
}
