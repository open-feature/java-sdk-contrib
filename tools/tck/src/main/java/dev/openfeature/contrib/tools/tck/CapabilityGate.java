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
     * <p>One skip, carrying its reason, is the whole mechanism. A capability that cannot hold in a
     * language at all — {@link Capability#LARGE_INTEGERS} on the Java SDK's 32-bit integer accessor
     * — is undeclared like any other the provider does not offer, and is skipped the same way.
     * Separating the two would ask a reader to learn a second vocabulary to be told what the
     * declaration and the scenario's own tags already say; where the impossibility is the
     * language's, Appendix F records it once instead.
     *
     * @param tags the scenario's Gherkin tags, including the leading at-sign
     * @param declared the capabilities the provider declares
     * @throws IllegalStateException if a tag names a reserved capability
     * @throws TestAbortedException if a tag gates an undeclared capability
     */
    public static void requireDeclared(Collection<String> tags, Set<Capability> declared) {
        requireNoExpiredReservation(tags);

        for (String tag : tags) {
            Optional<Capability> capability = Capability.fromTag(tag);
            if (!capability.isPresent()) {
                continue;
            }
            if (!declared.contains(capability.get())) {
                throw new TestAbortedException("Skipped: provider does not declare capability "
                        + capability.get().name() + " (tag " + tag + "). Declared capabilities: " + declared);
            }
        }
    }

    /**
     * Fails the run if a scenario carries the tag of a capability this suite still calls reserved.
     *
     * <p>This is the expiry check on {@link Capability#reserved()}, and it is the other half of
     * {@link Capability#requireDeclarable}. That one refuses a <em>declaration</em> naming a reserved
     * capability; this one refuses a <em>scenario</em> carrying its tag. A reservation is a name held
     * open for scenarios that do not exist yet and is only ever temporary — the specification writes
     * them, the tag starts gating something, and the capability becomes declarable. Until this
     * implementation follows, the two halves meet in the worst possible place: the scenario is
     * skipped for a capability no adopter is permitted to claim, a question put and silently
     * withdrawn. That is the unclaimable-capability failure
     * <a href="https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md">Appendix
     * F</a> describes.
     *
     * <p>Nothing else in the suite would notice it. The report is well-formed, the run is green, and
     * a capability-gated skip is explicitly not a gap — so the new scenario is executed by nobody and
     * the results say only what they say about every undeclared capability. It has no local symptom
     * at all, which is why it is checked rather than watched for: {@link Capability#TARGETING} was
     * reserved until the {@code targeting-key-flag} scenarios arrived.
     *
     * <p>Refused rather than worked around. Quietly treating the tag as declarable here would let a
     * run claim a capability against an implementation that does not know the tag exists; the point
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
