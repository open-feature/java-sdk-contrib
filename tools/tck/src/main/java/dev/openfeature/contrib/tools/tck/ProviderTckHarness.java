package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.FeatureProvider;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The lifecycle-agnostic contract a provider author implements to run the OpenFeature Provider TCK.
 *
 * <p>Two methods have no default: what provider to test, and what manipulates the backend it reads
 * from. Everything else is a convention with a working default. Nothing here mentions containers,
 * ports or HTTP — that belongs to {@link ContainerizedProviderTckTest}.
 *
 * <p>Extend {@link ContainerizedProviderTckTest} for a provider with an external backend, or
 * {@link ProviderTckTest} for one with none. Each base class is both the JUnit suite and the
 * harness, so no registration is needed. {@code tools/tck/README.md} carries a worked adoption for
 * each shape.
 *
 * @see ProviderTckTest
 * @see ContainerizedProviderTckTest
 */
public interface ProviderTckHarness {

    /**
     * Creates the provider under test, configured against a backend that is already running and
     * seeded with the canonical flag set.
     *
     * <p>Called once per scenario, so each gets its own instance; a factory rather than a field
     * because a Compose stack's host ports do not exist until it is up.
     *
     * <p>The TCK owns the provider lifecycle from here: it registers the provider with the
     * OpenFeature API under a scenario-scoped domain, waits for it to become ready, and shuts it
     * down afterwards. Do not call {@code setProvider} or {@code initialize} yourself.
     *
     * @return a configured, uninitialised provider
     */
    FeatureProvider createProvider();

    /**
     * Returns the seam through which the TCK manipulates the backend.
     *
     * <p>Called after {@link #startSuite()}, so an implementation may build it there and return the
     * same instance on every call. It must not be {@code null}.
     *
     * @return the backend control for this suite
     */
    BackendControl backendControl();

    /**
     * Creates a provider pointed at a backend that does not exist.
     *
     * <p>Point this at a closed port on localhost, with a short connection deadline: the scenario
     * allows a bounded time for {@code PROVIDER_ERROR} to arrive, and a provider with a 30-second
     * connect timeout will not make it. Do not point it at the backend under test — that must stay
     * up, and simulated outages belong to {@link BackendControl}.
     *
     * <p>Defaults to throwing; a harness that leaves {@link Capability#UNAVAILABLE_INIT} undeclared
     * never reaches it.
     *
     * @return a configured provider that cannot reach a backend
     */
    default FeatureProvider createUnavailableProvider() {
        throw new UnsupportedOperationException(getClass().getName() + " does not implement "
                + "createUnavailableProvider(). This is a test-configuration bug rather than a provider "
                + "defect: an @unavailable scenario ran, so the harness declared "
                + "Capability.UNAVAILABLE_INIT without supplying a provider that cannot reach its "
                + "backend. Remove that capability, or implement this method.");
    }

    /**
     * Declares which optional parts of the provider contract this provider supports.
     *
     * <p>Scenarios tagged with a capability that is not in this set are reported as
     * <strong>skipped</strong>. They are never silently passed.
     *
     * <p>Defaults to every {@linkplain Capability#declarable() declarable} capability. Narrow it
     * rather than widening it, and remove only what <em>your</em> provider cannot do:
     * {@link Capability#declarableExcept} is the idiomatic way to say "everything except". Do not
     * build the set with {@code EnumSet.allOf} or {@code EnumSet.complementOf} — both include the
     * {@linkplain Capability#reserved() reserved} and {@linkplain Capability#inexpressible()
     * inexpressible} tags, and declaring one fails the run.
     *
     * <p>Appendix F's
     * <a href="https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md">rules
     * for declaring</a> are what makes two reports comparable. Read them before narrowing this.
     *
     * @return the capabilities this provider supports
     */
    default Set<Capability> capabilities() {
        return Capability.declarable();
    }

    /**
     * Declares gaps this provider is known to have against parts of the contract the specification
     * does not treat as optional.
     *
     * <p>Declared so that a consumer can tell a design decision from a defect: from the outside a
     * capability the provider chose not to offer and one it cannot honour are the same absence, and
     * only the provider author knows which happened. Empty by default, which is silence rather than
     * a claim. See {@link KnownDeviation} for what counts as a requirement to deviate from.
     *
     * @return the deviations this provider acknowledges, empty by default
     */
    default List<KnownDeviation> knownDeviations() {
        return Collections.emptyList();
    }

    /**
     * Returns the name of the provider configuration this suite exercises.
     *
     * <p>The provider's configuration rather than its identity: which of its modes was tested, since
     * two materially different modes produce runs that are not interchangeable. Derived from the
     * suite class name by default — {@code MyProviderInProcessTest} becomes
     * {@code my-provider-in-process} — which is worth checking when the suite sits in a package that
     * already names the provider.
     *
     * @return a short name for this configuration
     */
    default String configuration() {
        return ReportNames.configurationOf(getClass());
    }

    /**
     * Prepares whatever must exist before the first scenario — a container stack, a temporary
     * directory, a local server.
     *
     * <p>Called once, before any scenario, and always paired with {@link #stopSuite()}.
     * {@link #backendControl()} is called immediately afterwards, so this is where to build it if it
     * needs something that only exists once the suite has started.
     */
    default void startSuite() {
        // Nothing to start by default.
    }

    /**
     * Releases whatever {@link #startSuite()} created.
     *
     * <p>Called once, after the last scenario, and also if suite startup fails partway through, so it
     * must tolerate being called when startup did not complete.
     */
    default void stopSuite() {
        // Nothing to stop by default.
    }

    /**
     * Returns how long to wait for a provider event to arrive.
     *
     * <p>Providers observe backend changes on wildly different timescales — a streaming provider in
     * milliseconds, one that polls every 30 seconds in most of a poll interval. Set it to
     * comfortably exceed your worst-case detection latency, or the suite reports timeouts that are
     * really just impatience. A scenario can tighten it with the explicit {@code within {int}ms}
     * step, which always wins.
     *
     * @return the default event await timeout, 12 seconds by default
     */
    default Duration eventTimeout() {
        return Duration.ofSeconds(12);
    }

    /**
     * Returns how long to wait for a provider to reach {@code READY} during initialisation.
     *
     * @return the readiness timeout, 30 seconds by default
     */
    default Duration readyTimeout() {
        return Duration.ofSeconds(30);
    }
}
