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
 * <h2>Two kinds of capability nobody may declare</h2>
 *
 * <p>Most entries here are an ordinary choice: declare it if your provider does it, leave it out if
 * it does not, and the results say which. Two are not a choice at all, and both are refused by
 * {@link #requireDeclarable} rather than left to an adopter to remember. They are refused for
 * different reasons, they are said differently, and they must not be confused with each other —
 * a reader who sees a capability missing from a report has to be able to tell <em>"this provider
 * declined"</em> from <em>"no provider in this language can be asked"</em>, because only the first
 * says anything about the provider.
 *
 * <p>An entry may be {@linkplain #reserved() reserved}: it exists in the vocabulary so that every
 * language's TCK spells the same property the same way, but <strong>no scenario anywhere carries
 * its tag yet</strong>. {@link #CACHING} is the only one left — {@link #TARGETING} was reserved
 * until the {@code targeting-key-flag} scenarios arrived, and is an ordinary declarable capability
 * now. A reservation is global and temporary: every language has it, and it expires the moment the
 * specification writes the scenarios.
 *
 * <p>An entry may instead be {@linkplain #inexpressible() inexpressible}: <strong>the scenarios
 * exist and are asked in other languages</strong>, but this SDK cannot put the question. {@link
 * #LARGE_INTEGERS} is the only one — {@code Client.getIntegerDetails} takes and returns a 32-bit
 * {@link Integer}, so 2^53 − 1 cannot be asked for by any Java provider whatever its backend serves.
 * That is one language's and permanent: it lasts until the SDK grows a wider accessor, and no
 * provider author can do anything about it. Refusing it centrally is what stops every Java adopter
 * having to know a fact about Java and act on it, and stops a single wrong one putting a claim in a
 * report that no scenario could have verified.
 * <a href="https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md">Appendix
 * F</a> states the rule.
 *
 * <p>Declare {@link #declarable()}, or {@link #declarableExcept} for "everything except", rather
 * than {@code EnumSet.allOf} or {@code EnumSet.complementOf}: both of the latter sweep up the
 * reserved and inexpressible tags on the way past, which is how a report comes to claim a capability
 * nobody examined.
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
     * handed its whole flag set by its constructor, or a facade over other providers — should
     * <strong>not</strong> declare it, however many events it emits.
     *
     * <p>The test is whether initialisation <em>acquires</em> something it did not already hold and
     * can be refused, not whether the thing acquired is across a socket. The TCK's own
     * {@code ControllableProviderTckTest} declares this against a store in the same JVM, because
     * that store is read at {@code initialize()} time and can decline — so {@code READY} is the
     * outcome of the call rather than a state the SDK manufactured. The SDK's
     * {@code InMemoryProvider} cannot, which is why {@code InMemoryProviderTckTest} withholds it.
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
     * skipped with that reason rather than passed. Either way the value assertions are unaffected:
     * they are untagged, and Requirement 2.2.3 makes the value a {@code MUST}. The reason is the same
     * shape of question one requirement further on, and it is gated the same way — see
     * {@link #STANDARD_REASONS}.
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
     * <p><strong>The value is asserted here, not the reason.</strong> The value rests on Requirement
     * 2.2.3, a {@code MUST}; pinning reason {@code DISABLED} on these rows would rest on 2.2.5, a
     * {@code SHOULD} that explicitly permits "some other string", and would narrow it for every
     * adopter. It is pinned in {@code gherkin/reason.feature} instead, on a scenario carrying both
     * this tag and {@code @standard-reasons}, so a provider opts into that narrowing rather than
     * inheriting it — see {@link #STANDARD_REASONS}. No variant is asserted either — a disabled flag
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
     * not be read as saying it is. The difference is still worth a word: narrowing {@code 0.5} to
     * {@code 0} with no error code hands an application a plausible value and no signal, so a
     * provider that does that should say whether it is a choice or a defect, and
     * {@link KnownDeviation} is where the second is said. Note which shape that takes — such a
     * provider <em>does</em> attempt the coercion and gets it wrong, so the honest report is to
     * declare the tag, let the lossy scenario fail, and record the deviation beside the failure
     * rather than withholding the tag to turn the failure into a skip.
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
     * <p><strong>{@linkplain #inexpressible() Inexpressible} in Java, so no Java provider may
     * declare it and {@link #requireDeclarable} refuses one that tries.</strong> Whether the value
     * can be asked for at all is a property of the SDK's integer accessor rather than of any
     * provider: {@code Client.getIntegerDetails} takes and returns a 32-bit {@link Integer}, so a
     * Java provider has nowhere to put {@code 9007199254740991} however faithfully its backend
     * serves it. Go's accessor is {@code int64} and JavaScript's number reaches 2^53 − 1 exactly, so
     * their suites declare it and run the scenario.
     *
     * <p><strong>This is not a reservation.</strong> The scenario exists, is asked, and passes
     * elsewhere; what is missing is a way to ask it here, and that will be missing until the Java
     * SDK grows a wider accessor. So the scenario is skipped, with a reason that says the SDK cannot
     * ask the question rather than that the provider declined — {@link CapabilityGate} keeps the two
     * apart, because only the second describes the provider under test.
     *
     * <p>Withholding it needs no {@link KnownDeviation}, and there is no longer anything for an
     * adopter to withhold: the refusal is here, once, instead of in each adoption's
     * {@code capabilities()} with a comment restating this paragraph.
     * <a href="https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md">Appendix
     * F</a> states the rule that puts it here.
     *
     * <p>The 32-bit precision scenario — {@code large-integer-flag}, 2^31 − 1 — is untagged and
     * always runs. What a provider owes a value that does not fit the requested accessor is the
     * open question in <a href="https://github.com/open-feature/spec/issues/430">open-feature/spec#430</a>.
     */
    LARGE_INTEGERS(
            "@large-integers",
            "Client.getIntegerDetails takes and returns a 32-bit Integer, so 9007199254740991 cannot be "
                    + "asked for by any Java provider, however faithfully its backend serves it"),

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
     * Provider reports the standard resolution reasons, with the meanings Appendix F gives them.
     *
     * <p>Gates {@code gherkin/reason.feature} in its entirety — and it is <strong>a claim, not an
     * exemption</strong>.
     * <a href="https://github.com/open-feature/spec/blob/main/specification/sections/02-providers.md">Requirement
     * 2.2.5</a> is a {@code SHOULD}, and it goes further than 2.2.4 does: it lets a provider populate
     * {@code reason} with one of the listed values <em>"or some other string indicating the semantic
     * reason for the returned flag value"</em>. A provider whose backend reports vendor-specific
     * reasons is therefore conformant, and asserting an exact reason against it would fail it for
     * something the specification permits.
     *
     * <p>An earlier revision of the suite asserted a reason in thirteen places across three feature
     * files, which narrowed that {@code SHOULD} into a {@code MUST} for every adopter. It bought very
     * little: every canonical flag resolves to a value distinct from the caller's default, so a
     * provider that silently falls back is already caught by the value assertion, and the reason only
     * said <em>why</em> it failed.
     *
     * <p>So declaring this is a provider saying <em>"I use the standard vocabulary with the standard
     * meanings"</em>, and {@code reason.feature} is what checks the claim. A provider that does not
     * declare it loses nothing: its values, variants and error codes are asserted everywhere else, on
     * {@code MUST} requirements. What the declaration adds is something a report's reader can act on —
     * anyone building telemetry, dashboards or debugging on {@code reason} can see that the vocabulary
     * was verified rather than assumed. Withholding it therefore needs no {@link KnownDeviation}.
     *
     * <p>The meanings are the content of the claim, and they constrain nobody who does not make it:
     *
     * <table border="1">
     *   <caption>The reason each situation is claimed to produce</caption>
     *   <tr><th>Situation</th><th>Reason</th></tr>
     *   <tr><td>The flag was resolved from configuration and carries no targeting rule</td>
     *       <td>{@code STATIC}</td></tr>
     *   <tr><td>A targeting rule matched the evaluation context</td><td>{@code TARGETING_MATCH}</td></tr>
     *   <tr><td>A targeting rule exists and did not match</td><td>{@code DEFAULT}</td></tr>
     *   <tr><td>The flag is disabled in the management system</td><td>{@code DISABLED}</td></tr>
     *   <tr><td>The evaluation failed, and an error code is reported with it</td><td>{@code ERROR}</td></tr>
     * </table>
     *
     * <p>{@code STATIC} for the first row is the call worth flagging.
     * <a href="https://github.com/open-feature/spec/blob/main/specification/types.md">{@code types.md}</a>
     * types {@code DEFAULT} as <em>"no dynamic evaluation occurred <strong>or</strong> dynamic
     * evaluation yielded no result"</em>, which a rule-less flag satisfies as readily as
     * {@code STATIC} does — two providers can disagree here and both conform. A provider that answers
     * {@code DEFAULT} for a rule-less flag is not defective; it does not use the standard meanings and
     * should not declare the tag.
     *
     * <p>{@code ERROR} is the row where the suite's subject is blurred, and it is asserted anyway. The
     * other four rest on
     * <a href="https://github.com/open-feature/spec/blob/main/specification/sections/01-flag-evaluation.md">Requirement
     * 1.4.7</a>, which makes the SDK propagate the provider's reason — but only <em>"in cases of normal
     * execution"</em>. Abnormal execution is 1.4.9, a {@code SHOULD} on the <strong>SDK</strong> to
     * indicate an error, and nothing requires the provider's reason to survive. So a passing
     * {@code ERROR} scenario establishes that the value reaching the application is coherent, not that
     * the provider produced it. It is still worth asserting: the error code alone is already covered
     * ungated in {@code errors.feature}, the reason alone could have been written by the SDK, and an
     * evaluation reporting {@code FLAG_NOT_FOUND} with reason {@code STATIC} is incoherent whoever
     * wrote it.
     *
     * <p><strong>Tags compose, and here that is load-bearing.</strong> {@code TARGETING_MATCH} cannot
     * be observed without targeting and {@code DISABLED} cannot be observed unless the backend
     * distinguishes a disabled flag, so those scenarios carry {@link #TARGETING} and
     * {@link #DISABLED_FLAGS} as well. A provider declaring this one alone runs the rest and skips
     * those two with their reason.
     *
     * <p>{@code SPLIT}, {@code UNKNOWN}, {@code CACHED} and {@code STALE} are not asserted. The first
     * two have no scenario that produces them; {@code CACHED} belongs behind {@link #CACHING} and needs
     * a repeat evaluation that nothing here performs, and {@code STALE} needs a scenario asserting what
     * a provider serves <em>during</em> an outage, which is the same gap.
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
     * Returns whether this capability is one the Java SDK cannot express, and so must not be
     * declared by any provider written against it.
     *
     * <p>The opposite case to {@link #reserved()}, and kept apart from it deliberately. A reserved
     * capability has no scenarios in any language and its reservation expires when the specification
     * writes them. An inexpressible one has scenarios that run and pass in other languages; what is
     * missing is a way to put the question through this SDK's API, and that lasts until the SDK
     * changes. Both are refused by {@link #requireDeclarable}, with different messages, and their
     * scenarios are skipped with different reasons.
     *
     * <p>It is the implementation that refuses it, rather than each adopter remembering to withhold
     * it. A property of the language is then recorded once, where it is true, instead of in every
     * suite that adopts the TCK — and an adopter cannot get it wrong in the one direction that
     * matters, which is claiming a capability no scenario could have verified.
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
     * declaration. {@linkplain #reserved() Reserved} capabilities are left out because no scenario
     * carries their tag; {@linkplain #inexpressible() inexpressible} ones because this SDK cannot
     * ask what they ask, so no Java provider could be held to them.
     *
     * <p>It is a set a Java provider may declare unchanged, and the default. Narrow it only for
     * things <em>this</em> provider cannot do — what no provider in this language can do has already
     * been taken out.
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
     * as well.
     *
     * <p>What belongs in {@code excluded} is a fact about <em>this provider</em>. A fact about Java
     * does not: {@link #LARGE_INTEGERS} is already absent, and naming it here is harmless but says
     * nothing, because no Java provider could have declared it.
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
     * <p>Fails the run rather than warning and dropping it. The declaration is the one part of a
     * conformance report that no result can check — everything else in it was observed, this is
     * asserted by the provider author — so a claim that cannot possibly be true is worth stopping
     * for. There is nothing to lose by refusing, either: in neither case below does any coverage
     * depend on the claim, and the fix is to call {@link #declarable()} or
     * {@link #declarableExcept}.
     *
     * <p><strong>Two claims are refused, for different reasons, and they are reported separately.</strong>
     * A {@linkplain #reserved() reserved} capability has no scenarios in any language; an
     * {@linkplain #inexpressible() inexpressible} one has scenarios that pass in other languages and
     * no way to ask them here. Collapsing them into one message would tell an adopter the two facts
     * are the same fact, and they behave differently: the first expires when the specification
     * writes the scenarios, the second when the SDK changes. Both lists are gathered before either
     * is thrown, so a declaration that gets both wrong hears about both.
     *
     * <p>Nothing else is refused. A capability whose scenario the provider cannot satisfy is not a
     * claim that cannot be checked — it is one the results contradict, which is what a conformance
     * run is for.
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
