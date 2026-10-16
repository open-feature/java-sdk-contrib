package dev.openfeature.contrib.tools.tck;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A declared gap: this provider fails a requirement (a {@code MUST}), as opposed to an optional
 * capability it chose not to offer. Declared via {@link ProviderTckHarness#knownDeviations()}.
 *
 * <p>Prefer declaring the capability and letting the scenario fail. {@link #capability} is
 * {@code null} for mandatory scenarios and must not be reserved or inexpressible. See
 * <a href="https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md">Appendix
 * F</a>.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class KnownDeviation {

    /** The capability tag the deviation concerns, or {@code null} when it maps to none. */
    public final String capability;

    /** Where the gap is tracked, or {@code null} when it is not tracked anywhere. */
    public final String issue;

    /** What the gap is; never {@code null}. */
    public final String summary;

    private KnownDeviation(Capability capability, String issue, String summary) {
        requireDeviable(capability);
        requireSummary(summary);
        this.capability = capability == null ? null : capability.tag();
        this.issue = issue;
        this.summary = summary;
    }

    /**
     * Refuses a deviation without a summary.
     *
     * @param summary the summary the adopter supplied
     * @throws IllegalArgumentException if it is null or blank
     */
    private static void requireSummary(String summary) {
        if (summary == null || summary.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "knownDeviations() records a deviation with no summary; describe what the provider gets wrong.");
        }
    }

    /**
     * Refuses a deviation against a reserved or inexpressible capability.
     *
     * @param capability the capability the deviation names, or {@code null}
     * @throws IllegalArgumentException if the capability is reserved or inexpressible
     */
    private static void requireDeviable(Capability capability) {
        if (capability == null) {
            return;
        }
        if (capability.reserved()) {
            throw new IllegalArgumentException("knownDeviations() records a deviation against reserved "
                    + capability.name() + " (" + capability.tag() + "), which no scenario in the suite "
                    + "carries; use null for a gap against a mandatory scenario.");
        }
        if (capability.inexpressible()) {
            throw new IllegalArgumentException("knownDeviations() records a deviation against "
                    + capability.name() + " (" + capability.tag() + "), which the Java SDK cannot "
                    + "express: " + capability.inexpressibleBecause() + "; its scenarios are never run "
                    + "against Java providers, so remove this deviation.");
        }
    }

    /**
     * Records a deviation tracked in an issue; the preferred form.
     *
     * @param capability the capability the gap is about, or {@code null} for none
     * @param issue a URI where the gap is tracked
     * @param summary what the gap is; required
     * @return the deviation, ready to declare
     * @throws IllegalArgumentException if the capability is reserved or inexpressible
     */
    public static KnownDeviation tracked(Capability capability, String issue, String summary) {
        return new KnownDeviation(capability, issue, summary);
    }

    /**
     * Records a deviation not yet tracked anywhere; prefer {@link #tracked}.
     *
     * @param capability the capability the gap is about, or {@code null} for none
     * @param summary what the gap is; required
     * @return the deviation, ready to declare
     * @throws IllegalArgumentException if the capability is reserved or inexpressible
     */
    public static KnownDeviation untracked(Capability capability, String summary) {
        return new KnownDeviation(capability, null, summary);
    }
}
