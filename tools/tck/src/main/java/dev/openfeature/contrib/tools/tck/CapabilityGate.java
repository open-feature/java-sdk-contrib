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
 * <p>A class of its own rather than inlined into the step definitions, so that the gate producing a
 * skip and the self-tests proving the skip survives into the results are looking at the same code.
 *
 * <p>Aborting rather than failing is what makes the outcome a skip: {@link TestAbortedException}
 * maps to {@code SKIPPED} in Cucumber's step results, which is what reaches the results.
 */
public final class CapabilityGate {

    private CapabilityGate() {}

    /**
     * Applies both gate rules to a scenario about to run.
     *
     * <p>First {@link #requireNoExpiredReservation}, then the declaration check below, and the
     * order is not interchangeable: a reserved capability can never be declared, so a reserved tag
     * examined second is always a skip for an undeclared capability and the expiry is never
     * reported. Both passes are over the whole tag list for the same reason.
     *
     * <p>Tags that gate nothing are ignored, so a scenario with no capability tag is mandatory and
     * always runs.
     *
     * <p>A capability the SDK {@linkplain Capability#inexpressible() cannot express} is skipped with
     * its own reason, which names the SDK rather than the provider, and is checked before the
     * declaration so that it is the reason every time.
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
     * <p>{@link #requireKnownVocabulary} is the only rule whose answer depends on where the
     * scenario came from, so the overload {@link #requireDeclared(Collection, Set)} passes no source
     * and that rule does not apply: without it, an adopter's own feature file would fail for using
     * its own tag.
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
                // Gates nothing. An unknown tag on a canonical scenario has already failed the
                // run in requireKnownVocabulary above, so what reaches here is an adopter's own.
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
     * <p><strong>An unknown tag gates nothing, so without this its scenarios stay mandatory for
     * every adopter.</strong> A suite that has not learned a new capability keeps demanding the old
     * behaviour, and the symptom is a provider that legitimately withholds the capability showing
     * unexplained failures while every other provider stays green, with nothing in the results
     * saying why.
     *
     * <p>Canonical scenarios only: an adopter's feature files under {@link ProviderTck#EXTENSIONS}
     * are expected to carry tags this vocabulary does not know, and failing those would make the
     * extension point unusable. {@link ProviderTck#FEATURES} holds the canonical set and nothing
     * else, which is why {@code EXTENSIONS} is a different name rather than a subdirectory of it.
     *
     * <p>Checked at run time as well as in this artifact's own tests, and the run-time half is not
     * redundant: {@code CanonicalTagCoverageTest} reads the assets packaged in <em>this</em> build,
     * and an adopter can put a {@code gherkin/} directory on a classpath root that shadows the
     * packaged one.
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
     * {@link ProviderTck#FEATURES}. Only the last two segments of the URI are looked at, so the
     * {@code classpath:} and {@code file:} forms need no special casing and a shadowing copy on
     * another classpath root is still canonical, which is the point. A {@code null} source is not
     * canonical, so a caller that has only a tag list does not assert this rule by accident.
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
     * <p>The other half of {@link Capability#requireDeclarable}: that one refuses a
     * <em>declaration</em> naming a reserved capability, this one a <em>scenario</em> carrying its
     * tag. When the specification writes the scenarios a reservation was holding the name open for,
     * the two halves meet in the worst place — the scenario is skipped for a capability no adopter
     * is permitted to claim, the report is well-formed and the run is green. It has no local symptom
     * at all, which is why it is checked rather than watched for.
     *
     * <p><strong>The tags are the parsed ones</strong>, from
     * {@link io.cucumber.java.Scenario#getSourceTagNames()}, so this cannot disagree with the run
     * about which tags a scenario carries — including tags inherited from the feature and tags on an
     * {@code Examples} block. Do not replace it with a text scan of the feature files:
     * {@code gherkin/events.feature} names {@code @caching} inside a Gherkin {@code #} comment, and
     * a text scan would fail every adoption over a sentence.
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
