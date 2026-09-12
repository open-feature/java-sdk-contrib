package dev.openfeature.contrib.tools.tck;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An entry that says: <strong>this provider fails to do something it is required to do.</strong>
 *
 * <p>The requirement has to be a numbered {@code MUST}, or a rule the implementation bound itself to
 * elsewhere — flagd's numeric-coercion ADR, say. Where the specification <em>permits</em> the
 * choice, withholding the capability <em>is</em> the honest report and a deviation entry would
 * assert a defect that does not exist. A provider that does not declare
 * {@code @configuration-change} has no streaming transport and is not pretending otherwise; a
 * provider that narrows {@code 0.5} to {@code 0} with no error code, having said elsewhere that it
 * coerces losslessly, has a defect.
 *
 * <h2>The two legitimate shapes</h2>
 *
 * <p>A run's results already distinguish them, so the entry does not have to say which:
 *
 * <ol>
 *   <li><strong>The capability is declared, the scenario runs, and it fails.</strong>
 *       <em>Prefer this.</em> The failure stays visible in the results and the deviation says it is
 *       known and why. A reader sees both the assertion that broke and the author's account of it.
 *   <li><strong>The capability is withheld, and its scenarios skip.</strong> Legitimate only when
 *       the provider cannot attempt the behaviour <em>at all</em>, so running the scenario would
 *       establish nothing — there is no connection to lose, no structured value to return. The
 *       deviation then explains the absence, so a reader can tell a defect from a design decision.
 * </ol>
 *
 * <p>Withdrawing a capability <em>in order to</em> turn a failing scenario into a skip is the
 * failure mode this field exists to prevent. If the provider attempts the behaviour and gets it
 * wrong, shape 1 is the honest report: declare the capability, let the scenario fail, and record the
 * deviation beside the failure.
 *
 * <h2>What the three fields are for</h2>
 *
 * <p>{@link #summary} is <strong>required</strong>. A deviation with no summary records that
 * something is wrong without saying what, which is worth less than the bare skip or failure it
 * accompanies.
 *
 * <p>{@link #issue} is <strong>optional</strong> — see {@link #tracked} and {@link #untracked}.
 * Naming an untracked defect is still what separates it from a choice; prefer the tracked form as
 * soon as there is an issue to point at.
 *
 * <p>{@link #capability} may be {@code null}, when the gap is against a mandatory, ungated scenario
 * and so belongs to no capability. It may <strong>not</strong> name a
 * {@linkplain Capability#reserved() reserved} capability: no scenario carries the tag, so there is
 * nothing to deviate from.
 *
 * <p>Declared by the provider author through {@link ProviderTckHarness#knownDeviations()}, because
 * that is the only place that knows. The TCK cannot infer any of this: from the outside, a
 * capability the provider chose not to offer and one it cannot honour are the same absence, and a
 * failing scenario says nothing about whether its author already knows.
 *
 * <p>Part of the declaration vocabulary rather than of any one consumer of it. This is something an
 * adopter <em>writes</em>, alongside {@link ProviderTckHarness#capabilities()}, so it belongs to the
 * suite an adopter adopts. Whatever reads the declaration — a machine-readable conformance report,
 * a build check, a human — is downstream of it and does not widen it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class KnownDeviation {

    /**
     * The capability tag the deviation concerns, or {@code null} when it maps to none.
     *
     * <p>Set whether the capability was declared and its scenario failed, or withheld and its
     * scenarios skipped. The results say which happened; this says which capability it was about.
     */
    public final String capability;

    /** Where the gap is tracked, or {@code null} when it is not tracked anywhere. */
    public final String issue;

    /** What the gap is, in a form someone comparing providers can use. Never {@code null}. */
    public final String summary;

    private KnownDeviation(String capability, String issue, String summary) {
        this.capability = capability;
        this.issue = issue;
        this.summary = summary;
    }

    /**
     * Records a deviation that is tracked somewhere. The preferred form.
     *
     * @param capability the capability the gap is about — declared and failing, or withheld and
     *     skipped — or {@code null} when the gap is against a mandatory, ungated scenario and so
     *     belongs to no capability. Must not be a {@linkplain Capability#reserved() reserved} one
     * @param issue a URI where the gap is tracked
     * @param summary what the gap is; required
     * @return the deviation, ready to declare
     */
    public static KnownDeviation tracked(Capability capability, String issue, String summary) {
        return new KnownDeviation(capability == null ? null : capability.tag(), issue, summary);
    }

    /**
     * Records a deviation that is not tracked anywhere yet.
     *
     * <p>Worth declaring even so. Naming the defect is what separates it from a capability the
     * provider chose not to offer, and a declaration that merely omits the tag cannot say which of
     * the two happened. Prefer {@link #tracked} as soon as there is an issue to point at.
     *
     * @param capability the capability the gap is about — declared and failing, or withheld and
     *     skipped — or {@code null} when the gap is against a mandatory, ungated scenario and so
     *     belongs to no capability. Must not be a {@linkplain Capability#reserved() reserved} one
     * @param summary what the gap is; required
     * @return the deviation, ready to declare
     */
    public static KnownDeviation untracked(Capability capability, String summary) {
        return new KnownDeviation(capability == null ? null : capability.tag(), null, summary);
    }
}
