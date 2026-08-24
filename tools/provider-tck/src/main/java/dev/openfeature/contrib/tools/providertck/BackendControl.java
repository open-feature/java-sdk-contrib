package dev.openfeature.contrib.tools.providertck;

import java.time.Duration;

/**
 * The single seam between the TCK's step definitions and whatever manipulates the backend.
 *
 * <p>Step definitions never talk to a backend directly. They talk to this interface, which is why
 * the same Gherkin can run unchanged against any backend an implementation can drive —
 * a containerised one over HTTP ({@link HttpBackendControl}) being the first. Nothing below this
 * line knows about ports, containers or transports.
 *
 * <h2>Which implementation is right for your provider</h2>
 *
 * <p>If your provider talks to a backend — a server, a service, anything out of process — use
 * {@link HttpBackendControl} by extending {@link ContainerizedProviderTckTest}. The HTTP control
 * API in {@code openapi/control-api.yaml} is the normative contract for those providers, and it is
 * what makes a conformance claim portable: another language's TCK drives the same endpoints against
 * the same stack and must get the same answers.
 *
 * <p><strong>Do not</strong> write a custom in-JVM {@code BackendControl} that reaches into an
 * external backend through a side channel — a test-only admin client, a shared database handle, a
 * static hook inside the provider. It will pass, and it will prove nothing, because the thing it
 * exercised is not the thing the contract describes.
 *
 * <p>An in-JVM implementation is legitimate only for providers that have <em>no</em> backend to
 * contract with: in-memory, environment-variable and file-based providers, where "the backend" is a
 * data structure in the same JVM.
 *
 * <h2>Operations a backend may not support</h2>
 *
 * <p>{@link #prepareScenario()} and {@link #changeFlag()} are mandatory: a backend that cannot reset
 * itself or change a flag cannot run the suite at all.
 *
 * <p>The three connection operations are not. A provider with nothing to disconnect from leaves
 * them at their defaults, which throw {@link UnsupportedOperationException}. That exception is a
 * <strong>test-configuration bug, never a skip</strong> — the scenarios that need connection
 * control are gated behind {@link Capability#STALE} and {@link Capability#UNAVAILABLE_INIT}, so
 * reaching one of these defaults means a capability was declared that the backend cannot back up.
 * Failing loudly there is deliberate: a silent no-op would report the scenario as passed.
 *
 * @see Capability
 * @see ProviderTckTest
 */
public interface BackendControl {

    /**
     * Brings the backend to the state every scenario starts from: reachable, with flag state at the
     * baseline of the canonical flag set.
     *
     * <p>Called once before each scenario. This is the TCK's only isolation mechanism — scenarios
     * share one backend for the whole suite, and containers are never restarted between them.
     */
    void prepareScenario();

    /**
     * Mutates flag configuration so that a conforming provider observes a configuration change and
     * resolves a different value for {@code changing-flag} afterwards.
     *
     * <p>Which value it changes to is deliberately unspecified; the suite asserts only that the
     * resolved value differs from what it was before.
     */
    void changeFlag();

    /**
     * Makes the backend unreachable for the rest of the scenario, without stopping any container.
     *
     * @throws UnsupportedOperationException if this backend has no connection to lose
     */
    default void disconnect() {
        throw unsupported("disconnect");
    }

    /**
     * Makes the backend reachable again after {@link #disconnect()}, preserving flag state so the
     * provider observes an availability change rather than a configuration change.
     *
     * @throws UnsupportedOperationException if this backend has no connection to restore
     */
    default void reconnect() {
        throw unsupported("reconnect");
    }

    /**
     * Makes the backend unreachable for a bounded period, after which it comes back on its own.
     *
     * @param outage how long the backend stays unreachable
     * @throws UnsupportedOperationException if this backend has no connection to lose
     */
    default void disconnectFor(Duration outage) {
        throw unsupported("disconnectFor");
    }

    /**
     * Returns a short description of what is being controlled, for startup logging and for the
     * failure messages of unsupported operations.
     *
     * @return a human-readable description of this backend control
     */
    default String description() {
        return getClass().getSimpleName();
    }

    /**
     * Builds the exception the connection-control defaults throw.
     *
     * @param operation the operation that is not supported
     * @return the exception to throw
     */
    default UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException(description() + " does not support '" + operation
                + "'. This is a test-configuration bug rather than a provider defect: a scenario "
                + "needing connection control ran, so the harness declared Capability.STALE or "
                + "Capability.UNAVAILABLE_INIT for a backend that cannot simulate an outage. "
                + "Remove those capabilities from the harness, or supply a BackendControl that "
                + "implements them.");
    }
}
