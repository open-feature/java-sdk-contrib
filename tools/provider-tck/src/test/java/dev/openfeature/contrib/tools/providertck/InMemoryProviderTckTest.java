package dev.openfeature.contrib.tools.providertck;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import java.util.EnumSet;
import java.util.Set;

/**
 * Runs the OpenFeature Provider TCK against the SDK's own {@link InMemoryProvider}.
 *
 * <p>This is the TCK's self-test, and it earns its keep twice over.
 *
 * <p>It is the <strong>reference adoption</strong> for a provider with no backend. Everything a
 * file-based or environment-variable provider needs to write is here, and it is three methods: hand
 * over a {@link BackendControl}, hand over a provider, and say which capabilities hold.
 *
 * <p>It is also the <strong>Docker-free canary</strong>. Because it needs no container, no Compose
 * stack and no network, it runs in seconds on any machine and in any CI job, which makes it the
 * fast check that catches a broken step definition, a mis-wired capability gate or a regression in
 * the shared harness long before the containerised suites get a chance to. When a change breaks
 * both this and the flagd suite, this one tells you within seconds and points at the TCK rather
 * than at a provider.
 *
 * <p>Note what it does <em>not</em> do: it is not a licence for providers that have a backend to
 * test themselves this way. See {@link BackendControl} for why.
 */
public class InMemoryProviderTckTest extends ProviderTckTest {

    /**
     * Both the flag store and the factory for the provider that serves it.
     *
     * <p>In-process the two are the same thing — {@code changeFlag()} has to reach the live provider
     * instance to emit an event from it — so one instance backs both harness methods below.
     */
    private final InProcessBackendControl control = new InProcessBackendControl();

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
     * <p>Four capabilities, and each omission is a fact about {@link InMemoryProvider} rather than a
     * convenience:
     *
     * <ul>
     *   <li>{@link Capability#LIFECYCLE} — omitted. Initialisation reaches no backend here, so the
     *       readiness scenario would pass without demonstrating anything, which is exactly what that
     *       capability exists to distinguish.
     *   <li>{@link Capability#STALE} — omitted. There is no connection to lose, so the provider can
     *       never go {@code STALE}. {@link InProcessBackendControl} leaves
     *       {@link BackendControl#disconnect()} unimplemented for the same reason, and this omission
     *       is what keeps the two consistent: the scenario is skipped before any step can reach the
     *       unsupported operation.
     *   <li>{@link Capability#UNAVAILABLE_INIT} — omitted. Initialisation cannot fail when there is
     *       nothing to connect to, so
     *       {@link ProviderTckHarness#createUnavailableProvider()} is left at its throwing default.
     *   <li>{@link Capability#TARGETING} and {@link Capability#CACHING} — omitted because no
     *       scenario carries their tags yet. Nothing is skipped by leaving them out today.
     * </ul>
     *
     * <p>{@link Capability#NUMERIC_COERCION} <em>is</em> declared, and that is worth stating
     * plainly: {@link InMemoryProvider} refuses to narrow {@code float-flag} (0.5) to an integer and
     * reports {@code TYPE_MISMATCH} instead. It is the reference behaviour the capability describes.
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(
                Capability.EVENTS,
                Capability.CONFIGURATION_CHANGE,
                Capability.OBJECT,
                Capability.NUMERIC_COERCION);
    }
}
