package dev.openfeature.contrib.tools.tck;

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
        requireNoExpiredReservation(tags);

        for (String tag : tags) {
            Optional<Capability> found = Capability.fromTag(tag);
            if (!found.isPresent()) {
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
