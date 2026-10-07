package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.FeatureProvider;
import java.util.EnumSet;
import java.util.Set;

/**
 * Runs the suite against a provider that has a real initialisation, with no Docker.
 *
 * <p>Covers the <strong>lifecycle</strong> feature without a container, which
 * {@link InMemoryProviderTckTest} cannot: its provider is handed its whole flag set by the
 * constructor, so the lifecycle scenarios would establish nothing and are skipped there. Without
 * this suite those step definitions are exercised only by a containerised provider suite, where a
 * break in them looks like a provider defect rather than a TCK one.
 *
 * <p>{@link ControllableProvider} closes that gap by acquiring its flag store at
 * {@code initialize()} time from a store that may refuse it. The store is in this JVM rather than
 * over a socket, so this is not a licence for a provider that does have a backend to test itself
 * this way — see {@link BackendControl}.
 *
 * <p>A superset of {@link InMemoryProviderTckTest}'s coverage rather than a replacement: that one
 * stays the <em>reference adoption</em>, written against the published
 * {@link InProcessBackendControl} and the SDK's own provider, and is the thing an adopter copies.
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
     * unreachability is the store's, which is all the {@code @unavailable} scenarios need.
     */
    @Override
    public FeatureProvider createUnavailableProvider() {
        return control.createUnavailableProvider();
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@link InMemoryProviderTckTest}'s set plus the three this suite exists for, each a fact
     * about {@link ControllableProvider} rather than a convenience:
     *
     * <ul>
     *   <li>{@link Capability#LIFECYCLE} — initialisation reaches a store it does not already hold
     *       and can be refused by it, so {@code READY} is the observable outcome of that call.
     *   <li>{@link Capability#REINITIALIZATION} — {@code shutdown()} drops the store and nothing
     *       else, and {@code initialize()} acquires a fresh copy.
     *   <li>{@link Capability#UNAVAILABLE_INIT} — {@link #createUnavailableProvider()} really does
     *       fail to initialise, so the {@code @unavailable} scenarios run instead of skipping.
     * </ul>
     *
     * <p>The omissions are {@link InMemoryProviderTckTest}'s, because every resolution decision here
     * is still the SDK provider's — this class adds a lifecycle and delegates all evaluation — with
     * one addition: {@link Capability#STALE}. This provider's store can refuse an
     * <em>initialisation</em>, which is what {@code @unavailable} needs, but it cannot take a
     * connection away from a running provider and put it back, so
     * {@link BackendControl#disconnect()} stays at its throwing default. It is the one capability
     * still without Docker-free coverage.
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
                Capability.DISABLED_FLAGS,
                Capability.STRING_TYPING,
                Capability.FULLY_TYPED_VALUES,
                Capability.STANDARD_REASONS);
    }
}
