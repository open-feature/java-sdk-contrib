package dev.openfeature.contrib.tools.tck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
    public String tag() {
        return tag;
    }

    /**
     * Returns whether this capability is reserved (no scenario carries it), and so must not be
     * declared.
     *
     * @return {@code true} if no scenario carries this capability's tag
     */
    public boolean reserved() {
        return reserved;
    }

    /**
     * Returns whether the Java SDK cannot express this capability, and so no provider may declare it.
     *
     * @return {@code true} if no provider written against this SDK can be asked this capability's
     *     scenarios
     */
    public boolean inexpressible() {
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
    public static Optional<Capability> fromTag(String tag) {
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
}
