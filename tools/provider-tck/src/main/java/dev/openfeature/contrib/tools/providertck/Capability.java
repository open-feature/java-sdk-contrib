package dev.openfeature.contrib.tools.providertck;

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
 * <p>Some entries are {@linkplain #reserved() reserved}: they exist in the vocabulary so that every
 * language's TCK spells the same property the same way, but no scenario carries their tag yet. A
 * reserved capability <strong>must not be declared</strong> — there is nothing for it to gate, so
 * declaring it cannot produce a skip and cannot be contradicted by any result. Declare
 * {@link #declarable()}, or {@link #declarableExcept} for "everything except", rather than
 * {@code EnumSet.allOf} or {@code EnumSet.complementOf}: both of the latter sweep up every reserved
 * tag on the way past, which is how a report comes to claim a capability nobody examined.
 *
 * <p>One entry is {@linkplain #notApplicable() not applicable} in Java: a scenario carries its tag,
 * but what the tag asks for is a property of the SDK rather than of any provider, and the Java SDK
 * cannot supply it. Such a capability is not declarable either — the claim could never be true of a
 * Java provider — and its scenarios are skipped with a reason that names the SDK, on every run.
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

    /** Provider emits lifecycle events at all ({@code PROVIDER_READY}, {@code PROVIDER_ERROR}). */
    EVENTS("@events"),

    /** Provider enters {@code STALE} and emits {@code PROVIDER_STALE} when the backend is lost. */
    STALE("@stale"),

    /** Provider detects flag configuration changes and emits {@code PROVIDER_CONFIGURATION_CHANGED}. */
    CONFIGURATION_CHANGE("@configuration-change"),

    /** Provider supports structured (object) flag values. */
    OBJECT("@object"),

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
     * <p>{@linkplain #notApplicable() Not applicable} in Java, and so not declarable. Whether the
     * value can be asked for at all is a property of the SDK's integer accessor rather than of the
     * provider: {@code Client.getIntegerDetails} takes and returns a 32-bit {@link Integer}, so a
     * Java provider has nowhere to put {@code 9007199254740991} however faithfully its backend
     * serves it. Go's accessor is {@code int64} and JavaScript's number reaches 2^53 − 1 exactly, so
     * their suites run the scenario; here it is reported as skipped, with that reason, on every run.
     *
     * <p>The 32-bit precision scenario — {@code large-integer-flag}, 2^31 − 1 — is untagged and
     * always runs. What a provider owes a value that does not fit the requested accessor is the
     * open question in <a href="https://github.com/open-feature/spec/issues/430">open-feature/spec#430</a>.
     */
    LARGE_INTEGERS(
            "@large-integers",
            "the Java SDK's integer accessor is a 32-bit Integer, so a Java provider cannot resolve an "
                    + "integer beyond 2^31 - 1 through it whatever its backend serves"),

    /**
     * Provider supports targeting rules driven by evaluation context.
     *
     * <p>{@linkplain #reserved() Reserved}. No scenario in the current suite carries this tag —
     * targeting is backend evaluation logic, which the TCK deliberately does not test. It exists so
     * the tag vocabulary stays aligned with the flagd test harness and so context-passthrough
     * scenarios have a home once the control API grows an echo endpoint.
     */
    TARGETING("@targeting", true),

    /**
     * Provider caches evaluation results and invalidates them on configuration change.
     *
     * <p>{@linkplain #reserved() Reserved}; no scenario carries this tag yet.
     */
    CACHING("@caching", true);

    private final String tag;
    private final boolean reserved;
    private final String notApplicableReason;

    Capability(String tag) {
        this(tag, false, null);
    }

    Capability(String tag, boolean reserved) {
        this(tag, reserved, null);
    }

    Capability(String tag, String notApplicableReason) {
        this(tag, false, notApplicableReason);
    }

    Capability(String tag, boolean reserved, String notApplicableReason) {
        this.tag = tag;
        this.reserved = reserved;
        this.notApplicableReason = notApplicableReason;
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
     * Returns whether this capability is one no Java provider can have, and so must not be declared.
     *
     * <p>Not applicable means a scenario carries the tag, but what it asks for is a property of the
     * SDK rather than of the provider and the Java SDK cannot supply it. Unlike a
     * {@linkplain #reserved() reserved} capability there <em>is</em> something to gate: the scenario
     * runs the gate and is reported as skipped with {@link #notApplicableReason()}, so a reader sees
     * why it was not examined rather than a bare omission.
     *
     * @return {@code true} if the Java SDK cannot satisfy this capability
     */
    public boolean notApplicable() {
        return notApplicableReason != null;
    }

    /**
     * Returns why this capability is not applicable in Java, when it is not.
     *
     * @return the reason, or empty for a capability a Java provider may declare
     */
    public Optional<String> notApplicableReason() {
        return Optional.ofNullable(notApplicableReason);
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
     * Returns every capability that may be declared: every capability some scenario gates and a
     * Java provider can have.
     *
     * <p>This, not {@code EnumSet.allOf(Capability.class)}, is what "everything" means for a
     * declaration. {@linkplain #reserved() Reserved} and {@linkplain #notApplicable() not
     * applicable} capabilities are left out.
     *
     * @return the declarable capabilities, as a fresh mutable set
     */
    public static EnumSet<Capability> declarable() {
        EnumSet<Capability> declarable = EnumSet.allOf(Capability.class);
        declarable.removeIf(capability -> capability.reserved() || capability.notApplicable());
        return declarable;
    }

    /**
     * Returns every declarable capability except the given ones.
     *
     * <p>The counterpart to {@code EnumSet.complementOf}, and the reason it exists: a provider
     * saying "everything except the one thing I cannot do" wants everything <em>declarable</em>
     * except that thing, whereas {@code complementOf} hands back the reserved and not-applicable
     * tags as well.
     *
     * @param excluded capabilities to withhold; reserved and not-applicable capabilities are absent
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
     * Rejects a declaration that names a reserved or a not-applicable capability.
     *
     * <p>Fails the run rather than warning and dropping it. The declaration is the one part of a
     * conformance report that no result can check — everything else in it was observed, this is
     * asserted by the provider author — so a claim that cannot possibly be true is worth stopping
     * for. There is nothing to lose by refusing, either: no scenario carries a reserved tag, and a
     * not-applicable one is skipped whatever is declared, so no coverage depends on the claim, and
     * the fix is to call {@link #declarable()} or {@link #declarableExcept}.
     *
     * @param declared the capabilities a harness declares
     * @throws IllegalArgumentException if any of them is reserved or not applicable
     */
    public static void requireDeclarable(Collection<Capability> declared) {
        List<String> reservedTags = new ArrayList<>();
        List<String> notApplicableTags = new ArrayList<>();
        for (Capability capability : declared) {
            if (capability.reserved()) {
                reservedTags.add(capability.name() + " (" + capability.tag() + ")");
            } else if (capability.notApplicable()) {
                notApplicableTags.add(
                        capability.name() + " (" + capability.tag() + "): " + capability.notApplicableReason);
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
        if (!notApplicableTags.isEmpty()) {
            throw new IllegalArgumentException("capabilities() declares " + notApplicableTags
                    + ", which no Java provider can satisfy: the limit is the SDK's, not the "
                    + "provider's, and the scenario is skipped with that reason whatever is declared. "
                    + "Leave it out — Capability.declarable() and Capability.declarableExcept(...) "
                    + "already do.");
        }
    }
}
