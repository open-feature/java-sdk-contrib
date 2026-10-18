package dev.openfeature.contrib.tools.tck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * An optional part of the OpenFeature provider contract that a provider may or may not support.
 *
 * <p>Each capability is one Gherkin tag, declared through {@link ProviderTckHarness#capabilities()}.
 * A scenario carrying an undeclared tag is reported as <em>skipped</em>, never as passed; an
 * untagged scenario is mandatory. The vocabulary, what each tag means and the rules for declaring
 * are
 * <a href="https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md">Appendix
 * F</a>'s; the javadoc below adds only what is specific to this SDK.
 *
 * <p>Two entries are not an adopter's choice: {@link #CACHING} is {@linkplain #reserved() reserved}
 * and {@link #LARGE_INTEGERS} is {@linkplain #inexpressible() inexpressible}. Build a declaration
 * with {@link #declarable()} or {@link #declarableExcept}, never with {@code EnumSet.allOf} or
 * {@code EnumSet.complementOf}, which sweep both up on the way past and fail the run.
 */
public enum Capability {

    /**
     * Provider performs an initialisation that reaches its backend, with an observable outcome.
     *
     * <p>The test is whether initialisation <em>acquires</em> something it did not already hold and
     * can be refused, not whether the thing acquired is across a socket.
     *
     * <p>Kept apart from {@link #EVENTS} because in Java the events say nothing:
     * {@code FeatureProviderStateManager} emits {@code PROVIDER_READY} and {@code PROVIDER_ERROR}
     * around {@code initialize} for any provider, {@code EventProvider} or not, so one with no
     * initialisation of its own reaches {@code READY} exactly as {@code NoOpProvider} would.
     */
    LIFECYCLE("@lifecycle"),

    /**
     * Provider can be initialised again after {@code shutdown} and serves flags afterwards.
     *
     * <p>Reuse is permitted rather than required, so withholding this needs no
     * {@link KnownDeviation}. Declaring {@link #LIFECYCLE} and withholding this one is the expected
     * combination for a provider whose initialisation reaches a backend it does not reopen.
     */
    REINITIALIZATION("@reinitialization"),

    /** Provider emits lifecycle events at all ({@code PROVIDER_READY}, {@code PROVIDER_ERROR}). */
    EVENTS("@events"),

    /** Provider enters {@code STALE} and emits {@code PROVIDER_STALE} when the backend is lost. */
    STALE("@stale"),

    /** Provider detects flag configuration changes and emits {@code PROVIDER_CONFIGURATION_CHANGED}. */
    CONFIGURATION_CHANGE("@configuration-change"),

    /** Provider supports structured (object) flag values. */
    OBJECT("@object"),

    /**
     * Provider names the variant it resolved.
     *
     * <p>Optional rather than required, so withholding it needs no {@link KnownDeviation}. The value
     * assertions are untagged and unaffected.
     */
    VARIANTS("@variants"),

    /**
     * Provider resolves a flag disabled in the management system to the caller's default value.
     *
     * <p><strong>Gated on whether the provider is told the flag was deliberately disabled</strong>,
     * which is the part of this tag no other document states. A provider that evaluates locally
     * reads the state itself; a provider whose backend decides can only substitute the caller's
     * default if the response distinguishes a disabled flag from an absent one, and where it does
     * not, withholding this needs no {@link KnownDeviation}. Check what the backend's response
     * actually carries before concluding it does not — a remote-evaluation protocol may say so
     * explicitly, as OFREP's {@code codeDefaultFlag} does.
     *
     * <p>Only the value is asserted. Reason {@code DISABLED} is pinned in
     * {@code gherkin/reason.feature} under {@link #STANDARD_REASONS} as well, and no variant is
     * asserted, so this capability and {@link #VARIANTS} deliberately do not compose.
     */
    DISABLED_FLAGS("@disabled-flags"),

    /** Provider reports an error state rather than hanging when initialised against a dead backend. */
    UNAVAILABLE_INIT("@unavailable"),

    /**
     * Provider coerces between the integer and float types only when the coercion is lossless.
     *
     * <p>Lossless coercion is permitted; lossy coercion must fail with {@code TYPE_MISMATCH}. The
     * rule is <strong>borrowed rather than normative</strong> — it is flagd's
     * <a href="https://github.com/open-feature/flagd/blob/main/docs/architecture-decisions/numeric-coercion.md">numeric
     * coercion ADR</a>, and no OpenFeature requirement says what a provider owes a value that does
     * not fit the accessor it was asked through
     * (<a href="https://github.com/open-feature/spec/issues/430">open-feature/spec#430</a>), so
     * withholding it is not a defect and needs no {@link KnownDeviation}. Appendix F carries the
     * rest.
     */
    NUMERIC_COERCION("@numeric-coercion"),

    /**
     * Provider reports {@code TYPE_MISMATCH} for a boolean or integer flag requested as a string,
     * rather than the value's string representation.
     *
     * <p>A claim about the <em>backend's</em> typing rather than about anything the SDK does: every
     * value has a string representation, so a backend that stores flag values as strings satisfies
     * the string accessor for every flag and has no mismatch to report. As with
     * {@link #NUMERIC_COERCION} the behaviour is not required
     * (<a href="https://github.com/open-feature/spec/issues/433">open-feature/spec#433</a>), so
     * withholding it needs no {@link KnownDeviation}.
     *
     * <p>Boolean and integer only — the cases a <em>partially</em> typed backend can still answer.
     * The float and structured cases carry {@link #FULLY_TYPED_VALUES} as well, so withholding this
     * tag skips all four and declaring it alone runs only these two.
     */
    STRING_TYPING("@string-typing"),

    /**
     * Backend records a native type for float and structured values too, so the string-typing
     * question can be asked of them.
     *
     * <p>Strictly narrower than {@link #STRING_TYPING} and always declared alongside it: the
     * scenarios it gates carry both tags, so declaring this one alone claims something no scenario
     * will check. Withholding it while declaring {@code STRING_TYPING} is the expected combination
     * for a partially typed store, and needs no {@link KnownDeviation} for the reason
     * {@code STRING_TYPING} gives. Appendix F has why the two questions are separate tags.
     */
    FULLY_TYPED_VALUES("@fully-typed-values"),

    /**
     * Provider resolves integers up to 2^53 − 1 exactly.
     *
     * <p><strong>{@linkplain #inexpressible() Inexpressible} in Java, so no Java provider may
     * declare it and {@link #requireDeclarable} refuses one that tries.</strong>
     * {@code Client.getIntegerDetails} takes and returns a 32-bit {@link Integer}, so a Java
     * provider has nowhere to put {@code 9007199254740991}. Refused centrally, so nothing is left
     * for an adoption to withhold and no {@link KnownDeviation} is owed.
     */
    LARGE_INTEGERS(
            "@large-integers",
            "Client.getIntegerDetails takes and returns a 32-bit Integer, so 9007199254740991 cannot be "
                    + "asked for by any Java provider, however faithfully its backend serves it"),

    /**
     * Provider resolves a flag differently for a matching evaluation context.
     *
     * <p>The only tag under which dropping the evaluation context is caught by a resolved value
     * rather than needing an echo endpoint. The targeting rule itself is specified by behaviour
     * rather than by syntax — see the
     * <a href="https://github.com/open-feature/spec/blob/main/specification/assets/provider-tck/README.md">canonical
     * flag set's README</a>.
     */
    TARGETING("@targeting"),

    /**
     * Provider reports the standard resolution reasons, with the meanings Appendix F gives them.
     *
     * <p>Gates {@code gherkin/reason.feature} in its entirety, and it is <strong>a claim rather than
     * an exemption</strong>: declaring it says "I use the standard vocabulary with the standard
     * meanings", and that file is what checks the claim. Reasons are permitted to be any string, so
     * withholding it needs no {@link KnownDeviation}. Appendix F's {@code @standard-reasons} section
     * holds the situation-to-reason table the claim is about.
     */
    STANDARD_REASONS("@standard-reasons"),

    /**
     * Provider caches evaluation results and invalidates them on configuration change.
     *
     * <p>{@linkplain #reserved() Reserved}; no scenario carries this tag yet.
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
     * Returns whether this capability is reserved, and so must not be declared.
     *
     * <p>Reserved means the tag is part of the shared vocabulary but no scenario carries it, so it
     * can gate nothing and listing it in a report would invite a reader to believe it was verified.
     *
     * @return {@code true} if no scenario carries this capability's tag
     */
    public boolean reserved() {
        return reserved;
    }

    /**
     * Returns whether this capability is one the Java SDK cannot express, and so must not be
     * declared by any provider written against it.
     *
     * <p>Kept apart from {@link #reserved()} deliberately: a reserved capability has no scenarios
     * anywhere and expires when the specification writes them, while an inexpressible one has
     * scenarios that pass elsewhere and lasts until this SDK changes. Both are refused by
     * {@link #requireDeclarable}, with different messages and different skip reasons.
     *
     * @return {@code true} if no provider written against this SDK can be asked this capability's
     *     scenarios
     */
    public boolean inexpressible() {
        return inexpressibleBecause != null;
    }

    /**
     * Returns why this SDK cannot express this capability, for the messages that have to say so.
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
     * Returns every capability a provider written against this SDK may declare.
     *
     * <p>This, not {@code EnumSet.allOf(Capability.class)}, is what "everything" means for a
     * declaration: {@linkplain #reserved() reserved} and {@linkplain #inexpressible() inexpressible}
     * capabilities are left out. Narrow it only for things <em>this</em> provider cannot do.
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
     * <p>Use this rather than {@code EnumSet.complementOf}, which hands back the reserved and
     * inexpressible tags as well.
     *
     * @param excluded capabilities to withhold; reserved and inexpressible capabilities are absent
     *     regardless
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
     * Rejects a declaration that claims something no result could check.
     *
     * <p>Fails the run rather than warning and dropping it: the declaration is the one part of a
     * conformance report that no result can check, so a claim that cannot possibly be true is worth
     * stopping for. The {@linkplain #reserved() reserved} and {@linkplain #inexpressible()
     * inexpressible} cases are reported separately because they are different facts that expire on
     * different events, and both lists are gathered before either is thrown.
     *
     * <p>Nothing else is refused. A capability whose scenario the provider cannot satisfy is one the
     * results contradict, which is what a conformance run is for.
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
                    + ", which no scenario in the suite carries. A reserved capability cannot be "
                    + "verified or contradicted by any result, so it must not be declared. Use "
                    + "Capability.declarable(), or Capability.declarableExcept(...) for "
                    + "\"everything except\" — EnumSet.allOf and EnumSet.complementOf pick reserved "
                    + "capabilities up on the way past.");
        }
        if (!inexpressibleTags.isEmpty()) {
            throw new IllegalArgumentException("capabilities() declares " + inexpressibleTags
                    + ", which the Java SDK cannot express. This is not a reserved capability: the "
                    + "scenarios exist and are asked in languages whose API is wide enough, so they "
                    + "say nothing about your provider and everything about the SDK it is written "
                    + "against. No Java provider can satisfy them until the SDK changes, so none may "
                    + "claim them — and you do not have to know that: Capability.declarable() "
                    + "already leaves them out, and their scenarios are skipped with a reason that "
                    + "names the SDK rather than your provider. Remove them from capabilities().");
        }
    }
}
