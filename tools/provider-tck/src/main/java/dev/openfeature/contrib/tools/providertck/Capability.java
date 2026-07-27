package dev.openfeature.contrib.tools.providertck;

import java.util.Arrays;
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
 */
public enum Capability {

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
     * Provider keeps the integer and float types distinct instead of coercing between them.
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
     */
    STRICT_NUMERIC_TYPING("@strict-numeric-typing"),

    /**
     * Provider supports targeting rules driven by evaluation context.
     *
     * <p>Reserved. No scenario in the current suite carries this tag — targeting is backend
     * evaluation logic, which the TCK deliberately does not test. It exists so the tag vocabulary
     * stays aligned with the flagd test harness and so context-passthrough scenarios have a home
     * once the control API grows an echo endpoint.
     */
    TARGETING("@targeting"),

    /**
     * Provider caches evaluation results and invalidates them on configuration change.
     *
     * <p>Reserved; no scenario carries this tag yet.
     */
    CACHING("@caching");

    private final String tag;

    Capability(String tag) {
        this.tag = tag;
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
     * Looks up the capability gated by a Gherkin tag.
     *
     * @param tag a Gherkin tag including the leading {@code @}
     * @return the matching capability, or empty if the tag does not gate a capability
     */
    public static Optional<Capability> fromTag(String tag) {
        return Arrays.stream(values()).filter(c -> c.tag.equals(tag)).findFirst();
    }
}
