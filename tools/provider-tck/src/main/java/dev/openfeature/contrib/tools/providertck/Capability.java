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
     * <p>Unlike the other entries here this is <strong>not</strong> an optional spec feature. The
     * specification requires a provider to report {@code TYPE_MISMATCH} when the requested type
     * cannot be satisfied, and narrowing {@code 0.5} to {@code 0} to satisfy an integer request
     * loses information silently — the worst possible failure mode for a feature flag, because the
     * application sees a plausible value and no error.
     *
     * <p>It is a capability only so that a provider with this defect can adopt the TCK today and
     * see the gap reported as an explicit skip, rather than being unable to adopt at all. Not
     * declaring it is an admission of a known bug, not a design choice. Declare it as soon as the
     * provider is fixed.
     *
     * <p><strong>Only the lossy half is tested.</strong> The canonical flag set contains no integral
     * float, so there is nothing to ask the lossless half of, and a provider that wrongly rejects
     * {@code 10.0} as an integer declares this and passes. Closing that gap means adding a flag to
     * the canonical set, which changes it for every language at once; Appendix F records it as open
     * rather than pretending it is covered.
     */
    NUMERIC_COERCION("@numeric-coercion"),

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
     * Returns every capability that may be declared, which is every capability some scenario gates.
     *
     * <p>This, not {@code EnumSet.allOf(Capability.class)}, is what "everything" means for a
     * declaration.
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
