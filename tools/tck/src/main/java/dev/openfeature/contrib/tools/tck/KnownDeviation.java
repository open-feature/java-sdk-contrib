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
 * <p><strong>The clearest illustration is one capability withheld twice, for two different
 * reasons.</strong> A provider with no streaming transport does not declare
 * {@code @configuration-change}: it has no way to notice a change and is not pretending otherwise,
 * so the skip is the whole report and there is nothing to deviate from. This module's own
 * {@code MultiProviderTckTest} withholds that same tag because {@code MultiProvider} extends
 * {@code EventProvider} and never subscribes to its children, so a child's
 * {@code PROVIDER_CONFIGURATION_CHANGED} is swallowed —
 * <a href="https://github.com/open-feature/java-sdk/issues/1882">open-feature/java-sdk#1882</a>,
 * a defect with a fix pending rather than a design. One skip, two meanings, and only the second is
 * something a reader has to be told.
 *
 * <p>That is the distinction. It is <em>not</em> a rule about which absences may carry a deviation:
 * a provider that attempts a behaviour and gets it wrong declares the capability and lets the
 * scenario fail — shape 1 below — rather than withholding it. flagd narrowing {@code 0.5} to
 * {@code 0} with no error code is that case, and the adoption in this repository declares
 * {@code @numeric-coercion} for exactly that reason. An earlier revision of
 * <a href="https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md">Appendix
 * F</a> illustrated the choice-against-defect distinction with a provider that <em>withheld</em>
 * {@code @numeric-coercion} because it narrows, and two of the four implementations followed it into
 * the withhold-plus-deviate combination this class exists to discourage. The appendix has since been
 * corrected and so has this paragraph.
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
 * and so belongs to no capability. It may <strong>not</strong> name a capability whose scenarios
 * were never put to this provider, and there are two of those, refused with different messages: a
 * {@linkplain Capability#reserved() reserved} one, where no scenario carries the tag in any
 * language, and an {@linkplain Capability#inexpressible() inexpressible} one, where the scenarios
 * exist and this SDK cannot ask them. Neither leaves anything to deviate from, and a deviation reads
 * as an admission of fault — here it would be a fault nobody committed and nobody could fix.
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
     * that the provider fails to do something it is required to do, so it has to be about a question
     * that was actually asked. Two are not, and they are refused separately because they are
     * different facts.
     *
     * <p>A {@linkplain Capability#reserved() reserved} capability has no scenarios in any language,
     * so there is nothing to deviate from. An {@linkplain Capability#inexpressible() inexpressible}
     * one has scenarios that run elsewhere and no way to put them through this SDK — so they were
     * never asked of this provider, and a deviation would assert a defect that could not have been
     * observed. Declaring it was already refused; recording a deviation against it is the same claim
     * by another route, and it is the more dangerous of the two, because a deviation reads as an
     * admission of fault and the fault here would belong to nobody.
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
     *     skipped — or {@code null} when the gap is against a mandatory, ungated scenario and so
     *     belongs to no capability. Must not be {@linkplain Capability#reserved() reserved} or
     *     {@linkplain Capability#inexpressible() inexpressible}: neither's scenarios were put to
     *     this provider
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
     *     skipped — or {@code null} when the gap is against a mandatory, ungated scenario and so
     *     belongs to no capability. Must not be {@linkplain Capability#reserved() reserved} or
     *     {@linkplain Capability#inexpressible() inexpressible}: neither's scenarios were put to
     *     this provider
     * @param summary what the gap is; required
     * @return the deviation, ready to declare
     * @throws IllegalArgumentException if the capability is reserved or inexpressible
     */
    public static KnownDeviation untracked(Capability capability, String summary) {
        return new KnownDeviation(capability, null, summary);
    }
}
