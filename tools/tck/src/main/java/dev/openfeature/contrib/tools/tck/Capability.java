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
 * F</a>'s; the javadoc below adds only what is specific to this SDK or to this implementation.
 *
 * <p>Two entries are not an adopter's choice at all, and {@link #requireDeclarable} refuses both
 * rather than leaving them to be remembered. A {@linkplain #reserved() reserved} one — {@link
 * #CACHING} — has no scenarios in any language; an {@linkplain #inexpressible() inexpressible} one —
 * {@link #LARGE_INTEGERS} — has scenarios that run elsewhere and no way to ask them through this
 * SDK. Build a declaration with {@link #declarable()} or {@link #declarableExcept}, never with
 * {@code EnumSet.allOf} or {@code EnumSet.complementOf}, which sweep both up on the way past.
 *
 * <p>{@link #STALE} and {@link #UNAVAILABLE_INIT} are the two that need a backend the provider can
 * be cut off from, so they are what a harness with an in-process {@link BackendControl} leaves
 * undeclared. Every step that would reach {@link BackendControl#disconnect()},
 * {@link BackendControl#reconnect()} or {@link ProviderTckHarness#createUnavailableProvider()} sits
 * in a scenario carrying one of the two; declaring them anyway surfaces as an
 * {@link UnsupportedOperationException} rather than a skip, which is deliberate.
 */
public enum Capability {

    /**
     * Provider performs an initialisation that reaches its backend, with an observable outcome.
     *
     * <p>The test is whether initialisation <em>acquires</em> something it did not already hold and
     * can be refused, not whether the thing acquired is across a socket. The TCK's own
     * {@code ControllableProviderTckTest} declares this against a store in the same JVM, because
     * that store is read at {@code initialize()} time and can decline. The SDK's
     * {@code InMemoryProvider} cannot, which is why {@code InMemoryProviderTckTest} withholds it.
     *
     * <p>Deliberately not {@link #EVENTS}, and in Java the reason is concrete:
     * {@code dev.openfeature.sdk.FeatureProviderStateManager} emits {@code PROVIDER_READY} and
     * {@code PROVIDER_ERROR} around {@code initialize} for <em>any</em> provider, whether or not it
     * is an {@code EventProvider}, so a provider with no initialisation of its own reaches
     * {@code READY} exactly as {@code NoOpProvider} would.
     */
    LIFECYCLE("@lifecycle"),

    /**
     * Provider can be initialised again after {@code shutdown} and serves flags afterwards.
     *
     * <p>Gates exactly one scenario, "A provider that was shut down can be initialized again", and
     * it is gated because
     * <a href="https://github.com/open-feature/spec/blob/main/specification/sections/02-providers.md">Requirement
     * 2.5.2</a> <strong>permits</strong> reuse rather than requiring it — so withholding it is a
     * choice and needs no {@link KnownDeviation}. Appendix F records why the scenario is gated
     * separately from {@link #LIFECYCLE} rather than left mandatory.
     *
     * <p>Declaring {@code LIFECYCLE} and withholding this one is the expected combination for a
     * provider whose initialisation reaches a backend it does not reopen. The scenario carries both
     * tags, so a provider declaring neither sees it skipped for {@code @lifecycle}.
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
     * <p>Gated because a variant is optional rather than required:
     * <a href="https://github.com/open-feature/spec/blob/main/specification/sections/02-providers.md">Requirement
     * 2.2.4</a> is a {@code SHOULD} and
     * <a href="https://github.com/open-feature/spec/blob/main/specification/types.md">{@code types.md}</a>
     * types the field as optional. Withholding it needs no {@link KnownDeviation}. The value
     * assertions are untagged and unaffected; the reason is the same shape of question one
     * requirement further on and is gated the same way — see {@link #STANDARD_REASONS}.
     */
    VARIANTS("@variants"),

    /**
     * Provider resolves a flag disabled in the management system to the caller's default value.
     *
     * <p><strong>Gated on whether the provider is told the flag was deliberately disabled</strong>,
     * which is the part of this tag no other document states. A provider that evaluates locally —
     * flagd's resolvers, an in-memory provider — reads the state itself. A provider whose backend
     * decides can only substitute the caller's default if the response distinguishes a disabled flag
     * from an absent one; where it does not, the provider has nothing to act on, and withholding
     * this needs no {@link KnownDeviation}.
     *
     * <p><strong>Do not assume a remote-evaluation protocol is in that position.</strong> The
     * obvious reading — the caller's default never leaves the process, so the server has nothing to
     * echo back — is wrong for at least one protocol: OFREP's {@code codeDefaultFlag} is a success
     * carrying a {@code reason} and no {@code value}, which tells the provider to use the code
     * default. {@code OfrepTest} in {@code providers/ofrep} has the protocol citation and the probed
     * response. Check what the response actually carries before concluding a provider cannot hold
     * this tag.
     *
     * <p>The value is asserted here and not the reason, because the value rests on a {@code MUST}
     * and the reason on a {@code SHOULD} that permits any string. Reason {@code DISABLED} is pinned
     * in {@code gherkin/reason.feature} instead, on a scenario carrying this tag and
     * {@code @standard-reasons} together. No variant is asserted either, so this capability and
     * {@link #VARIANTS} deliberately do not compose.
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
     * coercion ADR</a>, this capability is named after it, and no OpenFeature requirement says what
     * a provider owes a value that does not fit the accessor it was asked through
     * (<a href="https://github.com/open-feature/spec/issues/430">open-feature/spec#430</a>). A
     * provider that behaves differently is not violating the specification, and a report must not be
     * read as saying it is. Appendix F carries the rest, including which of declaring and
     * withholding is honest for which provider.
     *
     * <p>Java-specific consequence: a provider that keeps the two numeric types strictly apart in
     * both directions — as the SDK's own {@code InMemoryProvider} does, so the self-tests in this
     * module withhold the tag — cannot attempt the behaviour and needs no {@link KnownDeviation}.
     */
    NUMERIC_COERCION("@numeric-coercion"),

    /**
     * Provider reports {@code TYPE_MISMATCH} for a boolean or integer flag requested as a string,
     * rather than the value's string representation.
     *
     * <p>The same gap as {@link #NUMERIC_COERCION}, one type further out, and gated for a stronger
     * reason: every value has a string representation, so a backend that stores flag values as
     * strings satisfies the string accessor for <em>every</em> flag and has no mismatch to report.
     * Its flags are strings, and
     * <a href="https://github.com/open-feature/spec/blob/main/specification/sections/02-providers.md">Requirement
     * 2.2.3</a> asks it to populate {@code value} with the resolved flag value, which it did.
     *
     * <p>Nothing in the specification contradicts that, because the specification never says what
     * the type of a flag value <strong>is</strong>. {@code TYPE_MISMATCH} appears once, as a row in
     * the <a href="https://github.com/open-feature/spec/blob/main/specification/types.md">error code
     * table</a>, and no requirement obliges anyone to raise it; the only normative statement about
     * value type is
     * <a href="https://github.com/open-feature/spec/blob/main/specification/sections/01-flag-evaluation.md">Requirement
     * 1.3.4</a>, a {@code SHOULD} and on the <em>client</em> rather than the provider. So
     * <strong>a provider that withholds this tag is not violating the specification</strong> and
     * owes no {@link KnownDeviation} — the same instrument, and the same reasoning, as the numeric
     * rule it sits beside. The open question is
     * <a href="https://github.com/open-feature/spec/issues/433">open-feature/spec#433</a>.
     *
     * <p><strong>Boolean and integer only.</strong> Gates the two rows of the
     * {@code gherkin/errors.feature} Scenario Outline — {@code boolean-flag} and
     * {@code integer-flag} requested as strings. The float and structured cases carried this tag
     * too until specification revision {@code bda599f1} moved them behind
     * {@link #FULLY_TYPED_VALUES}; they still carry this one as well, so withholding it skips all
     * four and declaring it alone runs only these two. {@link #FULLY_TYPED_VALUES} says why the
     * one tag became two.
     *
     * <p>These two are the rows a <em>partially</em> typed backend can still answer: a boolean and
     * an integer are types such a store records natively, so failing them is the provider's own
     * doing rather than the backend's shape. That is what makes this tag worth asking separately —
     * a Flagsmith-backed provider records no native float or structure and yet does record these
     * two, and Java answers both.
     *
     * <p>Java-specific consequence: {@code Client.getStringDetails} is the one accessor every
     * backend can satisfy, so this tag is a claim about the <em>backend's</em> typing rather than
     * about anything the SDK does. A provider over a typed backend — flagd's resolvers, OFREP —
     * declares it; one over a backend that stores values as strings withholds it with the reason
     * recorded.
     */
    STRING_TYPING("@string-typing"),

    /**
     * Backend records a native type for float and structured values too, so the string-typing
     * question can be asked of them.
     *
     * <p>Strictly narrower than {@link #STRING_TYPING} and always declared alongside it: the two
     * scenarios this gates — {@code float-flag} and {@code object-flag} requested as strings —
     * carry both tags, so withholding either skips them. Declaring this one without
     * {@code STRING_TYPING} claims something no scenario will check.
     *
     * <p><strong>Why the split exists, since a single tag looks simpler.</strong> It was a single
     * tag, over all four cases, until specification revision {@code bda599f1}. Measurement across
     * three languages against one Flagsmith backend showed the problem: {@code float-flag} and
     * {@code object-flag} were stringified by every provider, because that store records no native
     * float or structure type and no provider over it can report a mismatch — a permitted absence.
     * {@code boolean-flag} and {@code integer-flag} were not: the store does record those two, Go
     * and Java answered them, and JavaScript returned {@code "true"} and {@code "10"} because of
     * its own code. Under one tag that provider withholds, and a real defect is published as a
     * permitted absence — the suite goes quiet on a bug. Appendix F states the general rule: a
     * capability coarser than the variation providers actually show hides defects inside permitted
     * absences.
     *
     * <p>So the unit of declaration is the question the <em>backend</em> can be asked, not the
     * accessor the SDK offers. Withholding this while declaring {@code STRING_TYPING} is the
     * expected combination for a partially typed store, and it needs no {@link KnownDeviation} for
     * the reason {@code STRING_TYPING} gives: the behaviour is not required.
     *
     * <p>Nothing about this is Java-specific. {@code Client.getStringDetails} asks all four cases
     * equally well; what differs is whether the backend has a type to mismatch against.
     */
    FULLY_TYPED_VALUES("@fully-typed-values"),

    /**
     * Provider resolves integers up to 2^53 − 1 exactly.
     *
     * <p><strong>{@linkplain #inexpressible() Inexpressible} in Java, so no Java provider may
     * declare it and {@link #requireDeclarable} refuses one that tries.</strong>
     * {@code Client.getIntegerDetails} takes and returns a 32-bit {@link Integer}, so a Java
     * provider has nowhere to put {@code 9007199254740991} however faithfully its backend serves it.
     * The scenario exists and passes in languages whose accessor is wide enough, which is what makes
     * this an inexpressible capability rather than a {@linkplain #reserved() reserved} one, and its
     * skip reason names the SDK rather than the provider — see {@link CapabilityGate}.
     *
     * <p>Refused centrally, per Appendix F, so nothing is left for an adoption to withhold and no
     * {@link KnownDeviation} is owed. No backend fixture would change the answer; only a wider SDK
     * accessor would. The 32-bit precision scenario — {@code large-integer-flag}, 2^31 − 1 — is
     * untagged and always runs.
     */
    LARGE_INTEGERS(
            "@large-integers",
            "Client.getIntegerDetails takes and returns a 32-bit Integer, so 9007199254740991 cannot be "
                    + "asked for by any Java provider, however faithfully its backend serves it"),

    /**
     * Provider resolves a flag differently for a matching evaluation context.
     *
     * <p>Gates the three {@code targeting-key-flag} scenarios, and it is the only tag under which
     * dropping the evaluation context is caught by a resolved value rather than needing an echo
     * endpoint. The flag's rule, and the fact that it is specified by behaviour rather than by
     * syntax, are in the
     * <a href="https://github.com/open-feature/spec/blob/main/specification/assets/provider-tck/README.md">canonical
     * flag set's README</a>; that context beyond the targeting key is still unverified is an open
     * question in Appendix F.
     */
    TARGETING("@targeting"),

    /**
     * Provider reports the standard resolution reasons, with the meanings Appendix F gives them.
     *
     * <p>Gates {@code gherkin/reason.feature} in its entirety, and it is <strong>a claim rather than
     * an exemption</strong>: declaring it says "I use the standard vocabulary with the standard
     * meanings", and that file is what checks the claim.
     * <a href="https://github.com/open-feature/spec/blob/main/specification/sections/02-providers.md">Requirement
     * 2.2.5</a> is a {@code SHOULD} that permits any string, so a provider that does not declare
     * this loses nothing and owes no {@link KnownDeviation} — its values, variants and error codes
     * are asserted everywhere else on {@code MUST} requirements.
     *
     * <p>Appendix F's {@code @standard-reasons} section holds the situation-to-reason table that is
     * the content of the claim, why {@code STATIC} rather than {@code DEFAULT} for a rule-less flag,
     * why {@code ERROR} is asserted even though the SDK may have written it, and which reasons are
     * not asserted at all. Two of the scenarios compose with {@link #TARGETING} and
     * {@link #DISABLED_FLAGS}, so a provider declaring this one alone runs the rest and skips those
     * two with their own reason.
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
     * <p>The opposite case to {@link #reserved()}, and kept apart from it deliberately: a reserved
     * capability has no scenarios anywhere and expires when the specification writes them, while an
     * inexpressible one has scenarios that pass elsewhere and lasts until this SDK changes. Both are
     * refused by {@link #requireDeclarable}, with different messages, and their scenarios are
     * skipped with different reasons.
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
     * capabilities are left out. It is the default, and a set a Java provider may declare unchanged.
     * Narrow it only for things <em>this</em> provider cannot do.
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
     * <p>The counterpart to {@code EnumSet.complementOf}, and the reason it exists: a provider
     * saying "everything except the one thing I cannot do" wants everything <em>declarable</em>
     * except that thing, whereas {@code complementOf} hands back the reserved and inexpressible tags
     * as well. What belongs in {@code excluded} is a fact about <em>this provider</em>.
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
     * stopping for, and the fix is to call {@link #declarable()} or {@link #declarableExcept}.
     *
     * <p>The {@linkplain #reserved() reserved} and {@linkplain #inexpressible() inexpressible} cases
     * are reported separately rather than in one message, because they are different facts and
     * expire on different events. Both lists are gathered before either is thrown, so a declaration
     * that gets both wrong hears about both.
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
