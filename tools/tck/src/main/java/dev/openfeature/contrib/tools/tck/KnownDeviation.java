package dev.openfeature.contrib.tools.tck;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An entry that says: <strong>this provider fails to do something it is required to do.</strong>
 *
 * <p>The requirement has to be a numbered {@code MUST}, or a rule the implementation bound itself to
 * elsewhere — flagd's numeric-coercion ADR, say. Where the specification <em>permits</em> the
 * choice, withholding the capability <em>is</em> the honest report and a deviation entry would
 * assert a defect that does not exist.
 *
 * <p>An entry is legitimate in two shapes, and <strong>the first is preferred</strong>: declare the
 * capability and let the scenario fail, or — only where the provider cannot attempt the behaviour at
 * all — withhold it and let the scenarios skip. Withdrawing a capability <em>in order to</em> turn a
 * failure into a skip is the failure mode this class exists to prevent.
 * <a href="https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md">Appendix
 * F</a> states both shapes and the rule behind them.
 *
 * <p>An illustration from this module, because it is the one case where the same absence means two
 * things. A provider with no streaming transport does not declare {@code @configuration-change} and
 * owes nothing further; {@code MultiProviderTckTest} withholds the same tag because
 * {@code MultiProvider} extends {@code EventProvider} and never subscribes to its children, so a
 * child's {@code PROVIDER_CONFIGURATION_CHANGED} is swallowed —
 * <a href="https://github.com/open-feature/java-sdk/issues/1882">open-feature/java-sdk#1882</a>.
 * Only the second is something a reader has to be told.
 *
 * <p>{@link #summary} is required; {@link #issue} is optional — see {@link #tracked} and
 * {@link #untracked}. {@link #capability} may be {@code null}, when the gap is against a mandatory,
 * ungated scenario. It may <strong>not</strong> name a {@linkplain Capability#reserved() reserved}
 * or {@linkplain Capability#inexpressible() inexpressible} capability: neither's scenarios were put
 * to this provider, so a deviation would assert a fault nobody committed.
 *
 * <p>Declared by the provider author through {@link ProviderTckHarness#knownDeviations()}, because
 * that is the only place that knows. Whatever reads the declaration — a conformance report, a build
 * check, a human — is downstream of it and does not widen it.
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

    private KnownDeviation(Capability capability, String issue, String summary) {
        requireDeviable(capability);
        this.capability = capability == null ? null : capability.tag();
        this.issue = issue;
        this.summary = summary;
    }

    /**
     * Refuses a deviation against a capability whose scenarios were never put to this provider.
     *
     * <p>The same rule as {@link Capability#requireDeclarable}, one step along: a deviation asserts
     * that the provider fails something it is required to do, so it has to be about a question that
     * was actually asked. Recording a deviation against a capability that could not be declared is
     * the same claim by another route, and the more dangerous of the two, because a deviation reads
     * as an admission of fault. The two cases are refused separately because they are different
     * facts.
     *
     * <p>Checked when the deviation is constructed rather than when it is read, so an adopter is told
     * at the point they wrote it and whether or not anything downstream ever reads the declaration.
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
                    + "carries. There is nothing to deviate from: no scenario was skipped for it and "
                    + "none failed. Use null for a gap against a mandatory, ungated scenario.");
        }
        if (capability.inexpressible()) {
            throw new IllegalArgumentException("knownDeviations() records a deviation against "
                    + capability.name() + " (" + capability.tag() + "), which the Java SDK cannot "
                    + "express: " + capability.inexpressibleBecause() + ". This is not a reserved "
                    + "capability — the scenarios exist and are asked in languages whose API is wide "
                    + "enough — but they were never put to your provider, so a deviation here asserts "
                    + "a defect that could not have been observed and that nobody could fix. The "
                    + "scenario's skip already says the SDK is the limit.");
        }
    }

    /**
     * Records a deviation that is tracked somewhere. The preferred form.
     *
     * @param capability the capability the gap is about — declared and failing, or withheld and
     *     skipped — or {@code null} when the gap belongs to no capability. Must not be
     *     {@linkplain Capability#reserved() reserved} or
     *     {@linkplain Capability#inexpressible() inexpressible}
     * @param issue a URI where the gap is tracked
     * @param summary what the gap is; required
     * @return the deviation, ready to declare
     * @throws IllegalArgumentException if the capability is reserved or inexpressible
     */
    public static KnownDeviation tracked(Capability capability, String issue, String summary) {
        return new KnownDeviation(capability, issue, summary);
    }

    /**
     * Records a deviation that is not tracked anywhere yet.
     *
     * <p>Worth declaring even so. Naming the defect is what separates it from a capability the
     * provider chose not to offer, and a declaration that merely omits the tag cannot say which of
     * the two happened. Prefer {@link #tracked} as soon as there is an issue to point at.
     *
     * @param capability the capability the gap is about — declared and failing, or withheld and
     *     skipped — or {@code null} when the gap belongs to no capability. Must not be
     *     {@linkplain Capability#reserved() reserved} or
     *     {@linkplain Capability#inexpressible() inexpressible}
     * @param summary what the gap is; required
     * @return the deviation, ready to declare
     * @throws IllegalArgumentException if the capability is reserved or inexpressible
     */
    public static KnownDeviation untracked(Capability capability, String summary) {
        return new KnownDeviation(capability, null, summary);
    }
}
