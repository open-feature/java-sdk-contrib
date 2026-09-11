package dev.openfeature.contrib.tools.providertck;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A gap the provider is known to have against something the specification does not treat as
 * optional.
 *
 * <p>Distinct from an undeclared capability, which is a <em>choice</em>. A provider that does not
 * declare {@code @configuration-change} has no streaming transport and is not pretending otherwise;
 * a provider that does not declare {@code @numeric-coercion} has a bug. Both look identical in
 * the results stream — scenarios skipped, reason recoverable from the declaration — so the
 * difference has to be stated, or a consumer cannot tell a design decision from a defect.
 *
 * <p>Declared by the provider author through {@link ProviderTckHarness#knownDeviations()}, which is
 * the only place that knows the difference. The TCK cannot infer it: from the outside, a capability
 * the provider chose to withhold and one it withheld because it is broken are the same absence.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class KnownDeviation {

    /** The capability tag the deviation concerns, or {@code null} when it maps to none. */
    public final String capability;

    /** Where the gap is tracked, or {@code null} when it is not tracked anywhere. */
    public final String issue;

    /** What the gap is, in a form someone comparing providers can use. */
    public final String summary;

    private KnownDeviation(String capability, String issue, String summary) {
        this.capability = capability;
        this.issue = issue;
        this.summary = summary;
    }

    /**
     * Records a deviation that is tracked somewhere.
     *
     * @param capability the capability withheld because of the gap, or {@code null} when the gap is
     *     against a mandatory scenario and so belongs to no capability
     * @param issue a URI where the gap is tracked
     * @param summary what the gap is
     * @return the deviation, ready to report
     */
    public static KnownDeviation tracked(Capability capability, String issue, String summary) {
        return new KnownDeviation(capability == null ? null : capability.tag(), issue, summary);
    }

    /**
     * Records a deviation that is not tracked anywhere yet.
     *
     * <p>Worth reporting even so. Naming the defect is what separates it from a capability the
     * provider chose to withhold, and a report that merely omits the tag cannot say which of the two
     * happened. Prefer {@link #tracked} as soon as there is an issue to point at.
     *
     * @param capability the capability withheld because of the gap, or {@code null} when the gap is
     *     against a mandatory scenario and so belongs to no capability
     * @param summary what the gap is
     * @return the deviation, ready to report
     */
    public static KnownDeviation untracked(Capability capability, String summary) {
        return new KnownDeviation(capability == null ? null : capability.tag(), null, summary);
    }
}
