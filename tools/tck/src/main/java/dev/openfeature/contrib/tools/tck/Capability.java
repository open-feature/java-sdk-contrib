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
 * <p>Not every provider implements every spec feature — a provider backed by a static file has no
 * meaningful notion of going stale, and a provider without a streaming transport cannot emit
 * configuration-change events. Rather than forcing such providers to fail scenarios they were never
 * going to satisfy, the TCK lets each one declare what it supports via
 * {@link ProviderTckHarness#capabilities()}.
 *
 * <p>Every capability corresponds to exactly one Gherkin tag. Scenarios carrying a tag whose
 * capability was not declared are aborted before they run and are reported as <em>skipped</em> —
 * never as passed. Silently green scenarios would make a conformance suite worthless.
 *
 * <p>Scenarios with no capability tag are considered mandatory and always run.
 *
 * <p>An entry may be {@linkplain #reserved() reserved}: it exists in the vocabulary so that every
 * language's TCK spells the same property the same way, but no scenario carries its tag yet.
 * {@link #CACHING} is the only one left — {@link #TARGETING} was reserved until the
 * {@code targeting-key-flag} scenarios arrived, and is an ordinary declarable capability now. A
 * reserved capability <strong>must not be declared</strong> — there is nothing for it to gate, so
 * declaring it cannot produce a skip and cannot be contradicted by any result. Declare
 * {@link #declarable()}, or {@link #declarableExcept} for "everything except", rather than
 * {@code EnumSet.allOf} or {@code EnumSet.complementOf}: both of the latter sweep up every reserved
 * tag on the way past, which is how a report comes to claim a capability nobody examined.
 *
 * <p>Some capabilities cannot hold in a language at all, as opposed to not holding for a particular
 * provider: {@link #LARGE_INTEGERS} asks for a value the Java SDK's 32-bit integer accessor has no
 * room for. That is a property of the SDK, true of every provider written against it, and
 * <a href="https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md">Appendix
 * F</a> is where it is recorded — once, rather than restated in every run. Here it is an ordinary
 * capability that a Java provider leaves undeclared, and its scenario is reported as skipped like
 * any other undeclared one.
 *
 * <h2>The connection-dependent capabilities</h2>
 *
 * <p>{@link #STALE} and {@link #UNAVAILABLE_INIT} are the two that require a backend the provider
 * can be cut off from. They are what a harness leaves undeclared when its {@link BackendControl}
 * has no connection to control — an in-memory, environment-variable or file-based provider, where
 * the backend is a data structure in the same JVM. Every step that would call
 * {@link BackendControl#disconnect()}, {@link BackendControl#reconnect()} or
 * {@link ProviderTckHarness#createUnavailableProvider()} lives in a scenario carrying one of these
 * two tags, so undeclaring them skips those scenarios before an unsupported operation can be
 * reached.
 *
 * <p>Getting that pairing wrong surfaces as an {@link UnsupportedOperationException} rather than a
 * skip, which is deliberate: it means a capability was declared that the harness cannot back up,
 * and that is a test-configuration bug.
 */
public enum Capability {

    /**
     * Provider performs an initialisation that reaches its backend, with an observable outcome.
     *
     * <p>Gates the lifecycle scenarios: reaching {@code READY} against a healthy backend, and
     * settling into {@code ERROR} — promptly, rather than blocking forever or throwing out of
     * provider registration — against one that cannot be reached.
     *
     * <p><strong>Why this is not {@link #EVENTS}.</strong> Gating these scenarios on {@code EVENTS}
     * is wrong in both directions. Too strict, because a stateless provider that emits no events of
     * its own — OFREP, for instance — still initialises against a backend and still owes the
     * contract; it simply cannot declare {@code EVENTS}. Too lax, because a provider that declares
     * {@code EVENTS} passes the readiness scenario <em>vacuously</em>:
     * {@code dev.openfeature.sdk.FeatureProviderStateManager} emits {@code PROVIDER_READY} and
     * {@code PROVIDER_ERROR} around {@code initialize} for <em>any</em> provider, whether or not it
     * is an {@code EventProvider}. A provider with no initialisation of its own therefore reaches
     * {@code READY} exactly as {@code NoOpProvider} would, and the scenario goes green having
     * demonstrated nothing about the provider.
     *
     * <p>So {@code EVENTS} asserts that the provider emits events; {@code LIFECYCLE} asserts that
     * there is a real initialisation behind the event whose outcome the events describe. Declare it
     * only if initialisation actually talks to the backend. A provider with nothing to reach — one
     * backed by an in-memory map, or a facade over other providers — should <strong>not</strong>
     * declare it, however many events it emits.
     */
    LIFECYCLE("@lifecycle"),

    /**
     * Provider can be initialised again after {@code shutdown} and serves flags afterwards.
     *
     * <p>Gates exactly one scenario, "A provider that was shut down can be initialized again", and
     * it is gated because the specification <strong>permits</strong> reuse rather than requiring it.
     * <a href="https://github.com/open-feature/spec/blob/main/specification/sections/02-providers.md">Requirement
     * 2.5.2</a> says a provider <em>SHOULD</em> revert to its uninitialized state after
     * {@code shutdown}, and its supporting text adds that <em>"some providers MAY allow
     * reinitialization from this state"</em>. A provider that releases its client on shutdown and
     * declines to start again is taking an option the specification offers it, so withholding this
     * capability is a choice and needs no {@link KnownDeviation}.
     *
     * <p><strong>Why this is not {@link #LIFECYCLE}.</strong> The scenario was originally untagged
     * — and therefore mandatory — on the reading that reverting to the uninitialized state is
     * observable as exactly one thing, being initialisable again. That inference does not hold, and
     * the cost of it was concrete: run against the flagd provider, whose
     * {@code FlagdProviderSyncResources} keeps {@code isInitialized} and {@code isShutDown} as
     * separate flags and refuses {@code initialize()} when either is set, the scenario failed and
     * was one step from being filed as a defect against a provider doing nothing wrong. A false
     * failure is the mirror image of a vacuous pass.
     *
     * <p>What the tag buys is the other direction. A provider that <em>does</em> offer reuse has
     * somewhere to be held to it, because "shutdown() releases the client and initialize() returns
     * early because an initialised flag was never cleared" is easy to write and leaves the provider
     * evaluating against a closed connection rather than failing outright. Reverting the state is
     * not separately observable — a provider that reverts but refuses reuse presents exactly as one
     * that did neither — so a gated reuse scenario is the only assertion the requirement admits.
     *
     * <p>Declaring {@code LIFECYCLE} and withholding this one is the expected combination for a
     * provider whose initialisation reaches a backend it does not reopen. The scenario carries both
     * tags, so a provider that declares neither sees it skipped for {@code @lifecycle} and loses
     * nothing by the second omission.
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
     * <p>Gated, because a variant is optional rather than required.
     * <a href="https://github.com/open-feature/spec/blob/main/specification/types.md">{@code types.md}</a>
     * declares the field <em>"variant (string, optional)"</em>, and
     * <a href="https://github.com/open-feature/spec/blob/main/specification/sections/02-providers.md">Requirement
     * 2.2.4</a> is a {@code SHOULD}: in normal execution a provider <em>"SHOULD populate the
     * resolution details structure's variant field"</em>. The same section adds that the value
     * <em>"might only be meaningful in the context of the flag management system associated with
     * the provider"</em>.
     *
     * <p>Some backends have no variant concept for a plain flag at all. Their evaluation response
     * carries no such key, so the provider never receives one and no amount of seeding can produce
     * one. Asserting a variant in every evaluation scenario failed such a backend ten times over for
     * something that is not a defect and that no provider author can fix — and left nothing to
     * record as a {@link KnownDeviation}, because there was no capability to hang one on.
     *
     * <p>A provider whose backend names its variants declares this and the {@code @variants}
     * scenario outline runs. One whose backend does not leaves it undeclared, and those rows are
     * skipped with that reason rather than passed. Either way the value and reason assertions are
     * unaffected: they are untagged, and Requirement 2.2.3 makes the value a {@code MUST}.
     */
    VARIANTS("@variants"),

    /**
     * Provider resolves a flag disabled in the management system to the caller's default value.
     *
     * <p>Gates one Scenario Outline, four rows: {@code disabled-boolean-flag},
     * {@code disabled-string-flag}, {@code disabled-integer-flag} and {@code disabled-float-flag},
     * each asked for with a default that differs from the value the flag is configured with. A
     * provider that ignores the state serves the configured value and is caught on the value alone.
     *
     * <p><strong>Gated because the answer is a property of architecture rather than of quality.</strong>
     * Where the substitution happens decides whether it can happen at all. A provider that evaluates
     * locally — flagd's RPC and in-process resolvers, an in-memory provider — holds the caller's
     * default in its own hands and can return it. A provider whose backend decides, one speaking
     * OFREP for instance, cannot: the default never leaves the process, so the server has nothing to
     * echo back and the provider has nothing to substitute. The same flag cannot behave the same way
     * across those two designs, and neither of them is wrong, so withholding this needs no
     * {@link KnownDeviation}.
     *
     * <p>Nothing in the specification says what a provider owes a disabled flag.
     * <a href="https://github.com/open-feature/spec/blob/main/specification/sections/01-flag-evaluation.md">Requirement
     * 1.4.7</a> is about the SDK propagating whatever reason arrived, and
     * <a href="https://github.com/open-feature/spec/blob/main/specification/sections/02-providers.md">Requirement
     * 2.2.5</a> only lists {@code DISABLED} among the reason strings a provider <em>may</em> use. So
     * <a href="https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md">Appendix
     * F</a> states the behaviour, as it does for {@link #NUMERIC_COERCION}, and gates it.
     *
     * <p><strong>The value is asserted, not the reason.</strong> The value rests on Requirement
     * 2.2.3, a {@code MUST}; pinning reason {@code DISABLED} would rest on 2.2.5, a {@code SHOULD}
     * that explicitly permits "some other string". No variant is asserted either — a disabled flag
     * resolved no variant, so there is none to name, and this capability and {@link #VARIANTS}
     * deliberately do not compose.
     */
    DISABLED_FLAGS("@disabled-flags"),

    /** Provider reports an error state rather than hanging when initialised against a dead backend. */
    UNAVAILABLE_INIT("@unavailable"),

    /**
     * Provider coerces between the integer and float types only when the coercion is lossless.
     *
     * <p>The rule is <strong>lossless coercion is permitted; lossy coercion must fail with
     * {@code TYPE_MISMATCH}</strong>. An integral float such as {@code 10.0} requested as an integer
     * must succeed, because nothing is lost by answering it; {@code 0.5} requested as an integer must
     * not, because narrowing it to {@code 0} discards the fractional part. The distinction is flagd's
     * <a href="https://github.com/open-feature/flagd/blob/main/docs/architecture-decisions/numeric-coercion.md">numeric
     * coercion ADR</a>, and this capability is named after it.
     *
     * <p>The rule is <strong>borrowed, not normative</strong>. OpenFeature has a single numeric type
     * and lets a typed language split it into two accessors "as idioms dictate", and nothing in the
     * specification says what a provider owes a value that does not fit the accessor it was asked
     * through — that is <a href="https://github.com/open-feature/spec/issues/430">open-feature/spec#430</a>.
     * A provider that behaves differently is not violating the specification, and a report must
     * not be read as saying it is. Withholding the capability is still worth a word: narrowing
     * {@code 0.5} to {@code 0} with no error code hands an application a plausible value and no
     * signal, so a provider that does that should say whether it is a choice or a tracked defect,
     * and {@link KnownDeviation} is where the second is said.
     *
     * <p><strong>Both halves are tested.</strong> The lossy half asks for {@code float-flag} (0.5)
     * as an integer and expects {@code TYPE_MISMATCH}; the lossless half asks for
     * {@code integral-float-flag} (10.0) as an integer and for {@code integer-flag} (10) as a float
     * and expects both to succeed. A provider declaring this must satisfy all three — rejecting
     * every float is an easy way to pass the first, and the other two are what stop it. A provider
     * that keeps the two numeric types strictly apart in both directions, as the SDK's own
     * {@code InMemoryProvider} does, therefore cannot declare it.
     */
    NUMERIC_COERCION("@numeric-coercion"),

    /**
     * Provider resolves integers up to 2^53 − 1 exactly.
     *
     * <p><strong>A Java provider leaves this undeclared.</strong> Whether the value can be asked for
     * at all is a property of the SDK's integer accessor rather than of the provider:
     * {@code Client.getIntegerDetails} takes and returns a 32-bit {@link Integer}, so a Java
     * provider has nowhere to put {@code 9007199254740991} however faithfully its backend serves it.
     * Go's accessor is {@code int64} and JavaScript's number reaches 2^53 − 1 exactly, so their
     * suites declare it and run the scenario.
     *
     * <p>That the limit is the language's is recorded in
     * <a href="https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md">Appendix
     * F</a> rather than in each run, so this is an ordinary declarable capability and withholding it
     * needs no {@link KnownDeviation}: the scenario is skipped for an undeclared capability, as it
     * would be in any language whose accessor was too narrow. Declaring it on a Java provider does
     * not fail the run — the value is simply unaskable and the scenario fails when
     * {@link TckValues} cannot convert it, which says the same thing louder.
     *
     * <p>The 32-bit precision scenario — {@code large-integer-flag}, 2^31 − 1 — is untagged and
     * always runs. What a provider owes a value that does not fit the requested accessor is the
     * open question in <a href="https://github.com/open-feature/spec/issues/430">open-feature/spec#430</a>.
     */
    LARGE_INTEGERS("@large-integers"),

    /**
     * Provider resolves a flag differently for a matching evaluation context.
     *
     * <p>Gates the three {@code targeting-key-flag} scenarios: a matching targeting key resolves
     * {@code hit}, a non-matching one resolves {@code miss}, and no context at all resolves
     * {@code miss} without erroring.
     *
     * <p>This is what makes context passthrough observable. Every other flag in the canonical set
     * resolves the same way whatever the context, so a provider that drops the context entirely
     * passes them all; here a matching context resolves to a different value, so dropping it is
     * caught by the resolved value itself rather than needing an echo endpoint on the control API.
     *
     * <p>The flag's rule is specified by behaviour rather than by syntax — resolve {@code hit} when
     * the targeting key is exactly {@code 5c3d8535-f81a-4478-a6d3-afaa4d51199e}, {@code miss}
     * otherwise — so a backend expresses it however it expresses targeting. A provider whose backend
     * has no targeting at all, or whose harness seeds a flag set that cannot carry a rule, leaves
     * this undeclared and the three scenarios are skipped with that reason.
     *
     * <p>What is still <em>not</em> covered is that the whole context arrives intact: a provider
     * that forwards the targeting key and silently discards every other attribute declares this and
     * passes. That gap needs either an echo operation on the control API or a second flag keyed on a
     * custom attribute.
     */
    TARGETING("@targeting"),

    /**
     * Provider caches evaluation results and invalidates them on configuration change.
     *
     * <p>{@linkplain #reserved() Reserved}; no scenario carries this tag yet.
     */
    CACHING("@caching", true);

    private final String tag;
    private final boolean reserved;

    Capability(String tag) {
        this(tag, false);
    }

    Capability(String tag, boolean reserved) {
        this.tag = tag;
        this.reserved = reserved;
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
     * <p>Reserved means the tag is part of the shared vocabulary but no scenario in the suite
     * carries it. Such a capability cannot gate anything: it produces no skip, so it plays no part
     * in reading the results, and listing it in a report invites a reader to believe it was verified
     * when nothing examined it.
     *
     * @return {@code true} if no scenario carries this capability's tag
     */
    public boolean reserved() {
        return reserved;
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
     * Returns every capability that may be declared: every capability some scenario gates.
     *
     * <p>This, not {@code EnumSet.allOf(Capability.class)}, is what "everything" means for a
     * declaration. {@linkplain #reserved() Reserved} capabilities are left out.
     *
     * <p>It is not a set any Java provider should declare unchanged. {@link #LARGE_INTEGERS} is in
     * it — it is a real capability, gating a real scenario — and the Java SDK's integer accessor has
     * no room for what it asks for, so withhold it with {@link #declarableExcept}.
     *
     * @return the declarable capabilities, as a fresh mutable set
     */
    public static EnumSet<Capability> declarable() {
        EnumSet<Capability> declarable = EnumSet.allOf(Capability.class);
        declarable.removeIf(Capability::reserved);
        return declarable;
    }

    /**
     * Returns every declarable capability except the given ones.
     *
     * <p>The counterpart to {@code EnumSet.complementOf}, and the reason it exists: a provider
     * saying "everything except the one thing I cannot do" wants everything <em>declarable</em>
     * except that thing, whereas {@code complementOf} hands back the reserved tags as well.
     *
     * @param excluded capabilities to withhold; reserved capabilities are absent regardless
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
     * Rejects a declaration that names a reserved capability.
     *
     * <p>Fails the run rather than warning and dropping it. The declaration is the one part of a
     * conformance report that no result can check — everything else in it was observed, this is
     * asserted by the provider author — so a claim that cannot possibly be true is worth stopping
     * for. There is nothing to lose by refusing, either: no scenario carries a reserved tag, so no
     * coverage depends on the claim, and the fix is to call {@link #declarable()} or
     * {@link #declarableExcept}.
     *
     * <p>Only reserved capabilities are refused. A capability whose scenario the provider cannot
     * satisfy is not a claim that cannot be checked — it is one the results contradict, which is
     * what a conformance run is for.
     *
     * @param declared the capabilities a harness declares
     * @throws IllegalArgumentException if any of them is reserved
     */
    public static void requireDeclarable(Collection<Capability> declared) {
        List<String> reservedTags = new ArrayList<>();
        for (Capability capability : declared) {
            if (capability.reserved()) {
                reservedTags.add(capability.name() + " (" + capability.tag() + ")");
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
    }
}
