package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.FeatureProvider;
import java.util.EnumSet;
import java.util.Set;

/**
 * Runs the suite against a provider that has a real initialisation, with no Docker.
 *
 * <p>This is the suite that covers the <strong>lifecycle</strong> feature without a container, and
 * it exists because {@link InMemoryProviderTckTest} cannot. The SDK's {@code InMemoryProvider} is
 * handed its whole flag set by its constructor, so its {@code initialize()} records a state and its
 * {@code shutdown()} releases nothing observable; running the lifecycle scenarios against it would
 * establish nothing, which is why that suite leaves {@link Capability#LIFECYCLE} undeclared and
 * why those scenarios were skipped there. Everything they assert — shutdown releases what
 * initialisation acquired, shutdown can be repeated, shutdown against a dead backend returns
 * promptly, and a provider that offers reuse really is reusable — therefore had no coverage at all
 * outside a containerised provider suite, where a break in those step definitions looks like a
 * provider defect rather than a TCK one.
 *
 * <p>{@link ControllableProvider} closes that gap by acquiring its flag store at
 * {@code initialize()} time from a store that may refuse it. The store is in this JVM rather than
 * over a socket, so this is not a licence for a provider that does have a backend to test itself
 * this way — see {@link BackendControl}. What it is, is the TCK exercising its own lifecycle steps
 * in seconds, on any machine, with no daemon.
 *
 * <p>It is a strict superset of {@link InMemoryProviderTckTest}'s coverage, not a replacement for
 * it: that one stays the <em>reference adoption</em> for a provider with no backend, written against
 * the published {@link InProcessBackendControl} and the SDK's own provider, and it is the thing an
 * adopter copies.
 *
 * <p>If you are porting this shape to another language, the one non-obvious constraint is that
 * {@link ControllableProvider} <strong>composes</strong> the SDK's in-memory provider instead of
 * extending it: seeding a subclass's flags during {@code initialize()} means calling
 * {@code updateFlags}, which emits {@code PROVIDER_CONFIGURATION_CHANGED}, so every initialisation
 * would fire a spurious configuration-change event at the very scenarios that assert which events
 * occur. That class's javadoc has the full reasoning and the emission it avoids.
 */
public class ControllableProviderTckTest extends ProviderTckTest {

    private final ControllableBackendControl control = new ControllableBackendControl();

    @Override
    public BackendControl backendControl() {
        return control;
    }

    @Override
    public FeatureProvider createProvider() {
        return control.createProvider();
    }

    /**
     * {@inheritDoc}
     *
     * <p>A provider over an in-JVM store that refuses to answer. No socket and no port: the
     * unreachability is the store's, which is all the {@code @unavailable} scenarios need — that
     * initialisation fails observably and promptly, that a code default still comes back with reason
     * {@code ERROR}, and that shutting such a provider down does not hang.
     */
    @Override
    public FeatureProvider createUnavailableProvider() {
        return control.createUnavailableProvider();
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@link InMemoryProviderTckTest}'s five, plus the three this suite exists for. Each addition
     * is a fact about {@link ControllableProvider} rather than a convenience:
     *
     * <ul>
     *   <li>{@link Capability#LIFECYCLE} — initialisation reaches a store it does not already hold,
     *       and can be refused by it, so {@code READY} is the observable outcome of that call rather
     *       than something the SDK manufactured for a provider with no initialisation step. That is
     *       the distinction the tag exists to draw, and it is why {@link InMemoryProviderTckTest}
     *       withholds it.
     *   <li>{@link Capability#REINITIALIZATION} — true here, and only declared because it is true:
     *       {@code shutdown()} drops the store and nothing else, {@code initialize()} acquires a
     *       fresh copy, and neither keeps a flag that would make the second call return early.
     *       Requirement 2.5.2 only <em>permits</em> reuse, so a provider that released something it
     *       could not recreate would leave this undeclared rather than record a
     *       {@link KnownDeviation}.
     *   <li>{@link Capability#UNAVAILABLE_INIT} — {@link #createUnavailableProvider()} really does
     *       fail to initialise, so the three {@code @unavailable} scenarios run instead of skipping.
     * </ul>
     *
     * <p>And the omissions, which are the same as {@link InMemoryProviderTckTest}'s because every
     * resolution decision here is still the SDK provider's — this class adds a lifecycle and
     * delegates all evaluation:
     *
     * <ul>
     *   <li>{@link Capability#NUMERIC_COERCION} — the delegate type-checks rather than coerces, so
     *       the two lossless scenarios would fail. Strict typing is a choice the SDK's reference
     *       provider is entitled to; the rule is borrowed from flagd's ADR rather than normative.
     *   <li>{@link Capability#STALE} — omitted. This provider's backend can refuse an
     *       <em>initialisation</em>, which is what {@code @unavailable} needs, but it cannot take a
     *       connection away from a running provider and put it back, which is what {@code @stale}
     *       needs. {@link BackendControl#disconnect()} therefore stays at its throwing default and
     *       the scenario is skipped before any step can reach it. Worth adding later; it is the one
     *       capability still without Docker-free coverage.
     *   <li>{@link Capability#TARGETING} — the delegate evaluates no rules, so
     *       {@code targeting-key-flag} resolves its {@code miss} variant whatever the context.
     *   <li>{@link Capability#LARGE_INTEGERS} — omitted, as every Java provider omits it: the limit
     *       is {@code Client.getIntegerDetails}'s 32 bits rather than this provider's.
     *   <li>{@link Capability#CACHING} — reserved, so not declarable, and nothing is skipped by
     *       leaving it out.
     * </ul>
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(
                Capability.EVENTS,
                Capability.LIFECYCLE,
                Capability.REINITIALIZATION,
                Capability.UNAVAILABLE_INIT,
                Capability.CONFIGURATION_CHANGE,
                Capability.OBJECT,
                Capability.VARIANTS,
                Capability.DISABLED_FLAGS);
    }
}
