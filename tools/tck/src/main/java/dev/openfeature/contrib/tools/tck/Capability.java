package dev.openfeature.contrib.tools.tck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.net.URI;
import java.util.Set;
import org.opentest4j.TestAbortedException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * An optional part of the provider contract, gated by one Gherkin tag and declared through
 * {@link ProviderTckHarness#capabilities()}. Scenarios with undeclared tags are skipped.
 *
 * <p>Tag semantics are defined by
 * <a href="https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md">Appendix
 * F</a>. Build declarations with {@link #declarable()} or {@link #declarableExcept}, not
 * {@code EnumSet.allOf}/{@code complementOf}, which include {@linkplain #reserved() reserved} and
 * {@linkplain #inexpressible() inexpressible} entries and fail the run.
 */
public enum Capability {

    /** Provider performs an initialisation that reaches its backend, with an observable outcome. */
    LIFECYCLE("@lifecycle"),

    /** Provider can be initialised again after {@code shutdown} and serves flags afterwards. */
    REINITIALIZATION("@reinitialization"),

    /** Provider emits lifecycle events at all ({@code PROVIDER_READY}, {@code PROVIDER_ERROR}). */
    EVENTS("@events"),

    /** Provider enters {@code STALE} and emits {@code PROVIDER_STALE} when the backend is lost. */
    STALE("@stale"),

    /** Provider detects flag configuration changes and emits {@code PROVIDER_CONFIGURATION_CHANGED}. */
    CONFIGURATION_CHANGE("@configuration-change"),

    /** Provider supports structured (object) flag values. */
    OBJECT("@object"),

    /** Provider names the variant it resolved. */
    VARIANTS("@variants"),

    /**
     * Provider resolves a flag disabled in the management system to the caller's default value.
     * Withhold it if the backend response cannot distinguish disabled from absent.
     */
    DISABLED_FLAGS("@disabled-flags"),

    /** Provider reports an error state rather than hanging when initialised against a dead backend. */
    UNAVAILABLE_INIT("@unavailable"),

    /**
     * Provider coerces between integer and float only when lossless; lossy coercion is a
     * {@code TYPE_MISMATCH}.
     */
    NUMERIC_COERCION("@numeric-coercion"),

    /**
     * Provider reports {@code TYPE_MISMATCH} for a boolean or integer flag requested as a string.
     */
    STRING_TYPING("@string-typing"),

    /**
     * Backend records native types for float and structured values too; always declared alongside
     * {@link #STRING_TYPING}.
     */
    FULLY_TYPED_VALUES("@fully-typed-values"),

    /**
     * Provider resolves integers up to 2^53 - 1 exactly. {@linkplain #inexpressible() Inexpressible}
     * in Java, since {@code Client.getIntegerDetails} is 32-bit.
     */
    LARGE_INTEGERS(
            "@large-integers",
            "Client.getIntegerDetails takes and returns a 32-bit Integer, so 9007199254740991 cannot be "
                    + "asked for by any Java provider, however faithfully its backend serves it"),

    /** Provider resolves a flag differently for a matching evaluation context. */
    TARGETING("@targeting"),

    /** Provider reports the standard resolution reasons, with the meanings Appendix F gives them. */
    STANDARD_REASONS("@standard-reasons"),

    /**
     * Provider caches evaluation results and invalidates them on configuration change.
     * {@linkplain #reserved() Reserved}; no scenario carries this tag yet.
     */
    CACHING("@caching", true);

    private final String tag;
    private final boolean reserved;
    private final String inexpressibleBecause;

    Capability(String tag) {
        this.tag = tag;
        this.reserved = false;
        this.inexpressibleBecause = null;
    }

    Capability(String tag, boolean reserved) {
        this.tag = tag;
        this.reserved = reserved;
        this.inexpressibleBecause = null;
    }

    Capability(String tag, String inexpressibleBecause) {
        this.tag = tag;
        this.reserved = false;
        this.inexpressibleBecause = inexpressibleBecause;
    }

    /**
     * Returns the Gherkin tag, including the leading {@code @}, that gates this capability.
     *
     * @return the Gherkin tag for this capability
     */
    String tag() {
        return tag;
    }

    /**
     * Returns whether this capability is reserved (no scenario carries it), and so must not be
     * declared.
     *
     * @return {@code true} if no scenario carries this capability's tag
     */
    boolean reserved() {
        return reserved;
    }

    /**
     * Returns whether the Java SDK cannot express this capability, and so no provider may declare it.
     *
     * @return {@code true} if no provider written against this SDK can be asked this capability's
     *     scenarios
     */
    boolean inexpressible() {
        return inexpressibleBecause != null;
    }

    /**
     * Returns why this SDK cannot express this capability.
     *
     * @return the reason, or {@code null} if this capability is expressible
     */
    String inexpressibleBecause() {
        return inexpressibleBecause;
    }

    /**
     * Looks up the capability gated by a Gherkin tag.
     *
     * @param tag a Gherkin tag including the leading {@code @}
     * @return the matching capability, or empty if the tag does not gate a capability
     */
    static Optional<Capability> fromTag(String tag) {
        return Arrays.stream(values()).filter(c -> c.tag.equals(tag)).findFirst();
    }

    /**
     * Returns every capability a provider may declare, excluding reserved and inexpressible ones.
     *
     * @return the declarable capabilities, as a fresh mutable set
     */
    public static EnumSet<Capability> declarable() {
        EnumSet<Capability> declarable = EnumSet.allOf(Capability.class);
        declarable.removeIf(capability -> capability.reserved() || capability.inexpressible());
        return declarable;
    }

    /**
     * Returns every declarable capability except the given ones.
     *
     * @param excluded capabilities to withhold
     * @return the declarable capabilities minus {@code excluded}, as a fresh mutable set
     */
    public static EnumSet<Capability> declarableExcept(Capability... excluded) {
        EnumSet<Capability> declared = declarable();
        for (Capability capability : excluded) {
            declared.remove(capability);
        }
        return declared;
    }

    /**
     * Rejects a declaration containing reserved or inexpressible capabilities.
     *
     * @param declared the capabilities a harness declares
     * @throws IllegalArgumentException if any of them is reserved or inexpressible
     */
    public static void requireDeclarable(Collection<Capability> declared) {
        List<String> reservedTags = new ArrayList<>();
        List<String> inexpressibleTags = new ArrayList<>();
        for (Capability capability : declared) {
            if (capability.reserved()) {
                reservedTags.add(capability.name() + " (" + capability.tag() + ")");
            } else if (capability.inexpressible()) {
                inexpressibleTags.add(
                        capability.name() + " (" + capability.tag() + "): " + capability.inexpressibleBecause());
            }
        }
        if (!reservedTags.isEmpty()) {
            throw new IllegalArgumentException("capabilities() declares reserved " + reservedTags
                    + ", which no scenario in the suite carries; use Capability.declarable() or "
                    + "Capability.declarableExcept(...) instead of EnumSet.allOf/complementOf.");
        }
        if (!inexpressibleTags.isEmpty()) {
            throw new IllegalArgumentException("capabilities() declares " + inexpressibleTags
                    + ", which the Java SDK cannot express. This is not a reserved capability: the "
                    + "scenarios exist and are asked in languages whose API is wide enough. "
                    + "Remove them from capabilities().");
        }
    }

    // The gate: whether a scenario counts, decided from its tags. Here because every rule is
    // about this vocabulary, so inexpressibleBecause() need not be public. Static, not a
    // ProviderTckHarness default, so an adopter cannot override the two checks that fail a run.


    /**
     * Applies the gate rules to a scenario of unknown source; the vocabulary check is skipped.
     *
     * @param tags the scenario's Gherkin tags, including the leading at-sign
     * @param declared the capabilities the provider declares
     * @throws IllegalStateException if a tag names a reserved capability
     * @throws TestAbortedException if a tag gates an inexpressible or an undeclared capability
     */
    static void requireDeclared(Collection<String> tags, Set<Capability> declared) {
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
    private static void requireKnownVocabulary(URI source, Collection<String> tags) {
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
    private static void requireNoExpiredReservation(Collection<String> tags) {
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
