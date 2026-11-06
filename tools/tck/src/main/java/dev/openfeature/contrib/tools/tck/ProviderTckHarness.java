package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.FeatureProvider;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The lifecycle-agnostic contract a provider author implements to run the Provider TCK.
 *
 * <p>Extend {@link ContainerizedProviderTckTest} for a provider with an external backend, or
 * {@link ProviderTckTest} for one with none. See {@code tools/tck/README.md}.
 *
 * @see ProviderTckTest
 * @see ContainerizedProviderTckTest
 */
public interface ProviderTckHarness {

    /**
     * Creates the provider under test, against a running backend seeded with the canonical flags.
     * Called once per scenario; do not call {@code setProvider} or {@code initialize}.
     *
     * @return a configured, uninitialised provider
     */
    FeatureProvider createProvider();

    /**
     * Returns the seam through which the TCK manipulates the backend. Called after
     * {@link #startSuite()}; must not be {@code null}.
     *
     * @return the backend control for this suite
     */
    BackendControl backendControl();

    /**
     * Creates a provider pointed at a closed port, with a short connection timeout. Only needed
     * if {@link Capability#UNAVAILABLE_INIT} is declared.
     *
     * @return a configured provider that cannot reach a backend
     */
    default FeatureProvider createUnavailableProvider() {
        throw new UnsupportedOperationException(getClass().getName() + " does not implement "
                + "createUnavailableProvider(); test-configuration bug: remove Capability.UNAVAILABLE_INIT "
                + "or implement this method.");
    }

    /**
     * Declares which optional capabilities this provider supports; scenarios for others are
     * skipped. Defaults to {@link Capability#declarable()}; narrow with
     * {@link Capability#declarableExcept}.
     *
     * @return the capabilities this provider supports
     */
    default Set<Capability> capabilities() {
        return Capability.declarable();
    }

    /**
     * Declares known gaps against required parts of the contract; see {@link KnownDeviation}.
     *
     * @return the deviations this provider acknowledges, empty by default
     */
    default List<KnownDeviation> knownDeviations() {
        return Collections.emptyList();
    }

    /**
     * Returns the name of the provider configuration (mode) this suite exercises. Derived from the
     * class name by default, e.g. {@code MyProviderInProcessTest} becomes
     * {@code my-provider-in-process}.
     *
     * @return a short name for this configuration
     */
    default String configuration() {
        return ReportNames.configurationOf(getClass());
    }

    /**
     * Prepares whatever must exist before the first scenario. Called once, paired with
     * {@link #stopSuite()}.
     */
    default void startSuite() {
        // nothing to start by default
    }

    /**
     * Releases whatever {@link #startSuite()} created; must tolerate a partial start.
     */
    default void stopSuite() {
        // nothing to stop by default
    }

    /**
     * Returns how long to wait for a provider event; should exceed worst-case detection latency.
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
