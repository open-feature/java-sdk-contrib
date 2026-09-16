package dev.openfeature.contrib.tools.tck;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.opentest4j.TestAbortedException;

/**
 * Decides what a scenario's tags mean for this run: skip it, fail it, or let it go ahead.
 *
 * <p>One implementation, deliberately. This is the rule the whole suite rests on — a scenario
 * skipped for an undeclared capability must be reported as skipped and never as passed — so the
 * gate that produces the skip and the tests that prove the skip survives into the results have to
 * be looking at the same code. Inlined into the step definitions, a self-test could only
 * demonstrate that some abort becomes a skip, not that <em>this</em> abort does.
 *
 * <p>Aborting rather than failing is what makes the outcome a skip: {@link TestAbortedException} maps
 * to {@code SKIPPED} in Cucumber's step results, which is what reaches the results.
 *
 * <p>The second rule here is the mirror of the first and fails rather than skips: a tag this
 * implementation still calls {@linkplain Capability#reserved() reserved} must never reach a
 * scenario. See {@link #requireNoExpiredReservation}.
 *
 * <p>The third produces a skip like the first but for a reason that has nothing to do with the
 * provider: a capability this SDK {@linkplain Capability#inexpressible() cannot express}. Its skip
 * reason is deliberately different from an undeclared capability's, because a report's reader has
 * to be able to tell them apart.
 *
 * <p>The fourth is the second one's own direction reversed, and fails too: a <em>canonical</em>
 * scenario carrying a tag this vocabulary does not know at all. See
 * {@link #requireKnownVocabulary}.
 */
public final class CapabilityGate {

    private CapabilityGate() {}

    /**
     * Applies both gate rules to a scenario about to run.
     *
     * <p>First {@link #requireNoExpiredReservation}, then the declaration check below. The order is
     * not interchangeable and is fixed here rather than left to the caller: a reserved capability
     * can never be declared, so a reserved tag examined second is always a skip for an undeclared
     * capability and the expiry is never reported. Both passes are over the whole tag list for the
     * same reason — a scenario tagged {@code @events @caching} against a provider that declares
     * neither would otherwise abort on the first tag and never look at the second.
     *
     * <p>Tags that gate nothing are ignored, so a scenario with no capability tag is mandatory and
     * always runs.
     *
     * <p><strong>Two skips, and they do not say the same thing.</strong> The ordinary one names the
     * provider, which did not declare the capability. The other names the SDK, which
     * {@linkplain Capability#inexpressible() cannot express} it — reporting that as "the provider
     * does not declare it" would read as a decision the provider took. It is checked before the
     * declaration, which makes it the reason every time rather than only when the provider happens
     * to have withheld the tag as well.
     *
     * @param tags the scenario's Gherkin tags, including the leading at-sign
     * @param declared the capabilities the provider declares
     * @throws IllegalStateException if a tag names a reserved capability
     * @throws TestAbortedException if a tag gates an inexpressible or an undeclared capability
     */
    public static void requireDeclared(Collection<String> tags, Set<Capability> declared) {
        requireDeclared(null, tags, declared);
    }

    /**
     * Applies every gate rule to a scenario about to run, knowing where the scenario came from.
     *
     * <p>The overload {@link #requireDeclared(Collection, Set)} calls into this one with no source,
     * which is every rule except {@link #requireKnownVocabulary} — that one is the only rule whose
     * answer depends on whether the scenario is canonical, and it cannot be applied to a scenario
     * of unknown origin without failing an adopter's own feature file for using its own tag.
     *
     * @param source the scenario's feature file, as Cucumber reports it, or {@code null} if unknown
     * @param tags the scenario's Gherkin tags, including the leading at-sign
     * @param declared the capabilities the provider declares
     * @throws IllegalStateException if a tag names a reserved capability, or if a canonical
     *     scenario carries a tag this vocabulary does not know
     * @throws TestAbortedException if a tag gates an inexpressible or an undeclared capability
     */
    public static void requireDeclared(URI source, Collection<String> tags, Set<Capability> declared) {
        requireNoExpiredReservation(tags);
        requireKnownVocabulary(source, tags);
        for (String tag : tags) {
            Optional<Capability> found = Capability.fromTag(tag);
            if (!found.isPresent()) {
                // Not a capability tag as far as this vocabulary is concerned, so it gates nothing
                // here. Skipping it is right for an adopter's own tag under extensions/ and wrong
                // for a canonical one, and the two are told apart by requireKnownVocabulary above
                // rather than here — by the time this loop runs, an unknown canonical tag has
                // already failed the scenario.
                continue;
            }
            Capability capability = found.get();
            if (capability.inexpressible()) {
                throw new TestAbortedException("Skipped: the Java SDK cannot express capability "
                        + capability.name() + " (tag " + tag + ") — " + capability.inexpressibleBecause()
                        + ". This scenario exists and is asked in languages whose API is wide enough, so "
                        + "this is not a reservation and not the provider under test declining: no Java "
                        + "provider can be asked it, and none may declare it.");
            }
            if (!declared.contains(capability)) {
                throw new TestAbortedException("Skipped: provider does not declare capability " + capability.name()
                        + " (tag " + tag + "). Declared capabilities: " + declared);
            }
        }
    }

    /**
     * Fails the run if a canonical scenario carries a tag this vocabulary does not know.
     *
     * <p>{@link #requireNoExpiredReservation} run backwards. That one catches a tag this
     * implementation knows and says nothing carries; this one catches a tag something carries and
     * this implementation does not know. Both end in a scenario whose gating is wrong in a way no
     * result reports, and this direction is the easier of the two to leave out — <strong>an unknown
     * tag gates nothing, so its scenarios stay mandatory for every adopter</strong>. A suite that
     * has not learned a new capability does not report a new capability; it silently keeps
     * demanding the old behaviour, and the symptom is a provider that legitimately withholds the
     * capability showing unexplained failures while every other provider stays green. Nothing in
     * the results says why. All four reference implementations ignored an unknown tag rather than
     * failing before Appendix F made this normative, and this package was one of them: the loop in
     * {@link #requireDeclared} did nothing but {@code continue}.
     *
     * <p><strong>Canonical scenarios only, and that restriction is not a weakening.</strong> The
     * extension point exists so an adopter can add feature files under
     * {@link ProviderTck#EXTENSIONS} with tags of its own, which this vocabulary is not supposed to
     * know — failing those would make the extension point unusable, and {@code DeclarationApiTest}
     * pins that a tag gating nothing is tolerated. What distinguishes them is the directory:
     * {@link ProviderTck#FEATURES} holds the canonical set and nothing else, which is why
     * {@code EXTENSIONS} is deliberately a different name rather than a subdirectory of it. A tag
     * in <em>there</em> that resolves to nothing is a capability this implementation has not
     * learned.
     *
     * <p>Checked at run time as well as in this artifact's own tests, and the run-time half is not
     * redundant: {@code CanonicalTagCoverageTest} reads the assets packaged in <em>this</em> build,
     * and an adopter can put a {@code gherkin/} directory on a classpath root that shadows the
     * packaged one. Appendix F also requires the check to be in force where the scenarios execute,
     * which the artifact's own test suite is not.
     *
     * @param source the scenario's feature file, as Cucumber reports it, or {@code null} if unknown
     * @param tags the scenario's Gherkin tags, including the leading at-sign
     * @throws IllegalStateException if the scenario is canonical and carries an unknown tag
     */
    public static void requireKnownVocabulary(URI source, Collection<String> tags) {
        if (!isCanonical(source)) {
            return;
        }
        List<String> unknown = new ArrayList<>();
        for (String tag : tags) {
            if (!Capability.fromTag(tag).isPresent()) {
                unknown.add(tag);
            }
        }
        if (unknown.isEmpty()) {
            return;
        }
        throw new IllegalStateException("The canonical scenario at " + source + " carries " + unknown
                + ", which this implementation's capability vocabulary does not know. An unknown tag "
                + "gates nothing, so without this check the scenario would stay mandatory for every "
                + "adopter — including one that legitimately cannot support the capability, which "
                + "would see unexplained failures while every other provider stayed green, and "
                + "nothing in the results would say why. The pinned specification revision has "
                + "added a capability this package has not: add it to the Capability enum, beside "
                + "the tag it was split from or grouped with, and say in its javadoc what declaring "
                + "it claims. If instead this is a feature file of your own, move it under "
                + ProviderTck.EXTENSIONS + "/ — " + ProviderTck.FEATURES + "/ is the canonical set "
                + "and is checked against the vocabulary.");
    }

    /**
     * Whether a scenario came from the canonical set rather than from an adopter's extension.
     *
     * <p>Decided on the feature file's immediate parent directory being
     * {@link ProviderTck#FEATURES}, over the URI Cucumber reports — {@code classpath:gherkin/
     * errors.feature} for the packaged assets, a {@code file:} URI when the features are read from
     * a directory. Only the last two segments are looked at, so neither form needs special casing
     * and a shadowing copy on another classpath root is still canonical, which is the point.
     *
     * <p>A {@code null} source is not canonical. It is what the two-argument
     * {@link #requireDeclared(Collection, Set)} passes, and the callers that use it are tests
     * asserting the other three rules over a bare tag list; treating an unknown origin as canonical
     * would make those assert this rule by accident.
     */
    private static boolean isCanonical(URI source) {
        if (source == null) {
            return false;
        }
        String path = source.toString().replace('\\', '/');
        int lastSeparator = path.lastIndexOf('/');
        if (lastSeparator < 0) {
            return false;
        }
        String parent = path.substring(0, lastSeparator);
        int start = Math.max(parent.lastIndexOf('/'), parent.lastIndexOf(':')) + 1;
        return ProviderTck.FEATURES.equals(parent.substring(start));
    }

    /**
     * Fails the run if a scenario carries the tag of a capability this suite still calls reserved.
     *
     * <p>The expiry check on {@link Capability#reserved()}, and the other half of
     * {@link Capability#requireDeclarable}: that one refuses a <em>declaration</em> naming a reserved
     * capability, this one a <em>scenario</em> carrying its tag. An
     * {@linkplain Capability#inexpressible() inexpressible} capability has no equivalent and could
     * not — a scenario carrying its tag is exactly what is expected, since other languages run it.
     *
     * <p>When the specification writes the scenarios a reservation was holding the name open for and
     * this implementation has not followed, the two halves meet in the worst possible place: the
     * scenario is skipped for a capability no adopter is permitted to claim — the
     * unclaimable-capability failure
     * <a href="https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md">Appendix
     * F</a> describes. Nothing else in the suite would notice: the report is well-formed, the run is
     * green, and a capability-gated skip is explicitly not a gap. It has no local symptom at all,
     * which is why it is checked rather than watched for — {@link Capability#TARGETING} was reserved
     * until the {@code targeting-key-flag} scenarios arrived.
     *
     * <p>Refused rather than worked around: treating the tag as declarable here would let a run
     * claim a capability against an implementation that does not know the tag exists, and the point
     * of the check is that a human re-reads the reserved list against the specification.
     *
     * <p><strong>The tags are the parsed ones.</strong> They come from
     * {@link io.cucumber.java.Scenario#getSourceTagNames()}, which is the same parse the run itself
     * is driven by, so this cannot disagree with the run about which tags a scenario carries —
     * including tags inherited from the feature and tags on an {@code Examples} block. A check that
     * scanned the feature files as text instead would be wrong on the day it was written:
     * {@code gherkin/events.feature} names {@code @caching} inside a Gherkin {@code #} comment,
     * explaining which scenarios are deliberately not covered yet, and a text scan would fail every
     * adoption over a sentence.
     *
     * @param tags the scenario's Gherkin tags, including the leading at-sign
     * @throws IllegalStateException if a tag names a reserved capability
     */
    public static void requireNoExpiredReservation(Collection<String> tags) {
        List<String> expired = new ArrayList<>();
        for (String tag : tags) {
            Optional<Capability> capability = Capability.fromTag(tag);
            if (capability.isPresent() && capability.get().reserved()) {
                expired.add(capability.get().name() + " (" + capability.get().tag() + ")");
            }
        }
        if (expired.isEmpty()) {
            return;
        }
        throw new IllegalStateException("This scenario carries reserved " + expired
                + ", so the scenarios that reservation was held open for now exist. A reserved "
                + "capability cannot be declared — Capability.requireDeclarable refuses it — so "
                + "without this check the scenario would be reported as skipped for a capability no "
                + "adopter is permitted to claim, which is the unclaimable-capability failure "
                + "Appendix F describes and which nothing else in this suite would notice. If the "
                + "tag arrived with the canonical feature files, drop the reserved flag from that "
                + "constant in Capability so an adoption can declare it and be held to it. If it "
                + "arrived from a feature file of your own under extensions/, pick a tag of your "
                + "own: a reserved tag gates nothing and cannot be declared.");
    }
}
