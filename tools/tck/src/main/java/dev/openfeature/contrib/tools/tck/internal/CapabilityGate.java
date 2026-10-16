package dev.openfeature.contrib.tools.tck.internal;

import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.ProviderTck;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.opentest4j.TestAbortedException;

/**
 * Decides whether a scenario is skipped, failed, or run based on its tags; skips are signalled with
 * {@link TestAbortedException}.
 */
public final class CapabilityGate {

    private CapabilityGate() {}

    /**
     * Applies the gate rules to a scenario of unknown source; the vocabulary check is skipped.
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
     * Applies all gate rules to a scenario; reservation is checked first, since reserved tags can
     * never be declared.
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
                // adopter tag; gates nothing
                continue;
            }
            Capability capability = found.get();
            if (capability.inexpressible()) {
                throw new TestAbortedException("Skipped: the Java SDK cannot express capability "
                        + capability.name() + " (tag " + tag + "); " + capability.inexpressibleBecause()
                        + ". This is not the provider under test declining.");
            }
            if (!declared.contains(capability)) {
                throw new TestAbortedException("Skipped: provider does not declare capability " + capability.name()
                        + " (tag " + tag + "). Declared capabilities: " + declared);
            }
        }
    }

    /**
     * Fails the run if a canonical scenario carries a tag unknown to {@link Capability}; extension
     * scenarios are exempt.
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
                + ", unknown to the Capability enum; add the capability to the enum, or move your own "
                + "feature file under " + ProviderTck.EXTENSIONS + "/.");
    }

    // canonical if the parent directory is FEATURES; null is not canonical
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
     * Fails the run if a scenario carries the tag of a reserved capability, which could otherwise
     * never be declared and would be silently skipped.
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
                + "; drop the reserved flag in Capability if the tag is canonical, or use a tag of "
                + "your own in extensions/.");
    }
}
