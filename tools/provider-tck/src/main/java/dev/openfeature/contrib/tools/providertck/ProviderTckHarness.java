package dev.openfeature.contrib.tools.providertck;

import dev.openfeature.sdk.FeatureProvider;
import java.time.Duration;
import java.util.Set;

/**
 * The lifecycle-agnostic contract a provider author implements to run the OpenFeature Provider TCK.
 *
 * <p>Two methods have no default: what provider to test, and what manipulates the backend it reads
 * from. Everything else is a convention with a working default. Nothing here mentions containers,
 * ports or HTTP — that belongs to {@link ContainerizedProviderTckTest}, which implements this
 * interface in terms of a Compose stack.
 *
 * <p>Which base class to extend:
 *
 * <ul>
 *   <li>Your provider talks to an external backend — extend {@link ContainerizedProviderTckTest}.
 *       It brings the Compose lifecycle, port discovery and {@link HttpBackendControl}, and the
 *       HTTP control API stays the normative contract for your conformance claim.
 *   <li>Your provider has no backend (in-memory, environment variables, a local file) — extend
 *       {@link ProviderTckTest} directly and supply an in-process {@link BackendControl}.
 * </ul>
 *
 * <p>Implementations are discovered through the executing JUnit suite, and through
 * {@link java.util.ServiceLoader} as a fallback. Extend one of the two base classes — each is both
 * the JUnit suite and the harness — and no registration is needed.
 *
 * <p>Example — the entire adoption for a backend-less provider:
 *
 * <pre>{@code
 * public class MyProviderTckTest extends ProviderTckTest {
 *
 *     private final InProcessBackendControl control = new InProcessBackendControl();
 *
 *     @Override
 *     public BackendControl backendControl() {
 *         return control;
 *     }
 *
 *     @Override
 *     public FeatureProvider createProvider() {
 *         return control.createProvider();
 *     }
 *
 *     @Override
 *     public Set<Capability> capabilities() {
 *         return EnumSet.of(Capability.EVENTS, Capability.CONFIGURATION_CHANGE, Capability.OBJECT);
 *     }
 * }
 * }</pre>
 *
 * @see ProviderTckTest
 * @see ContainerizedProviderTckTest
 */
public interface ProviderTckHarness {

    /**
     * Creates the provider under test, configured against a backend that is already running and
     * seeded with the canonical flag set.
     *
     * <p>Called once per scenario. This is a factory rather than a field because a provider cannot
     * always be configured before the suite starts — a Compose stack's host ports do not exist
     * until it is up — and because each scenario gets its own provider instance.
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
     * <p>Used by the initialisation-failure scenarios, which assert that a provider that cannot
     * reach its backend settles into {@code ERROR} and emits {@code PROVIDER_ERROR} rather than
     * hanging or throwing out of {@code setProvider}.
     *
     * <p>Point this at a closed port on localhost. Do not point it at the backend under test — that
     * must stay up and reachable, and simulated outages belong to {@link BackendControl}.
     *
     * <p>Configure a short connection deadline. The scenario allows a bounded time for the error
     * event to arrive, and a provider with a 30-second connect timeout will not make it.
     *
     * <p>Defaults to throwing, because a provider with no backend has no way to be unreachable.
     * Such a harness leaves {@link Capability#UNAVAILABLE_INIT} undeclared and the scenarios that
     * would call this are reported as skipped, so the default is never reached. Reaching it means a
     * capability was declared that the harness cannot back up.
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
     * rather than widening it: start from the default, run the suite, and remove only what your
     * provider genuinely cannot do — {@link Capability#declarableExcept} is the idiomatic way to say
     * "everything except".
     *
     * <p>Do not build the set with {@code EnumSet.allOf} or {@code EnumSet.complementOf}. Both
     * include the {@linkplain Capability#reserved() reserved} capabilities, which no scenario
     * carries, and declaring one of those fails the run.
     *
     * @return the capabilities this provider supports
     */
    default Set<Capability> capabilities() {
        return Capability.declarable();
    }

    /**
     * Prepares whatever must exist before the first scenario — a container stack, a temporary
     * directory, a local server.
     *
     * <p>Called once, before any scenario, and always paired with {@link #stopSuite()}. Defaults to
     * doing nothing, which is right for a harness whose backend is a data structure in this JVM.
     *
     * <p>{@link #backendControl()} is called immediately afterwards, so this is where to build it
     * if it needs something that only exists once the suite has started.
     */
    default void startSuite() {
        // Nothing to start by default.
    }

    /**
     * Releases whatever {@link #startSuite()} created.
     *
     * <p>Called once, after the last scenario, and also if suite startup fails partway through, so
     * it must tolerate being called when startup did not complete.
     */
    default void stopSuite() {
        // Nothing to stop by default.
    }

    /**
     * Returns how long to wait for a provider event to arrive.
     *
     * <p>This is the single most important knob for a provider author, because providers observe
     * backend changes on wildly different timescales. A streaming provider sees a configuration
     * change in milliseconds; a provider that polls every 30 seconds may need most of a poll
     * interval before it notices. Set this to comfortably exceed your worst-case detection latency,
     * or the suite will report timeouts that are really just impatience.
     *
     * <p>Individual scenarios can tighten this with the explicit {@code within {int}ms} step, which
     * always wins over this value.
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
