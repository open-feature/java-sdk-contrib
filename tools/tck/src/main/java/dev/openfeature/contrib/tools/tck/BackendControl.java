package dev.openfeature.contrib.tools.tck;

/**
 * The single seam between the TCK's step definitions and whatever manipulates the backend.
 *
 * <p>Step definitions never talk to a backend directly. They talk to this interface, which is why
 * the same Gherkin runs unchanged against a containerised backend driven over HTTP
 * ({@link HttpBackendControl}) and against a provider manipulated in-process
 * ({@link InProcessBackendControl}). Nothing below this line knows about ports, containers or
 * transports.
 *
 * <h2>Which implementation is right for your provider</h2>
 *
 * <p>A provider that talks to a backend uses {@link HttpBackendControl} by extending
 * {@link ContainerizedProviderTckTest}; in-process control is for a provider with <em>no</em>
 * backend to contract with — see {@link InProcessBackendControl}. That allowance is narrow, and
 * <a href="https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md">Appendix
 * F</a> says why a custom in-JVM control reaching an external backend through a side channel passes
 * while proving nothing.
 *
 * <h2>Operations a backend may not support</h2>
 *
 * <p>{@link #prepareScenario()}, {@link #changeFlag()} and {@link #controlApi()} are mandatory. The
 * two connection operations are not: a provider with nothing to disconnect from leaves them at
 * their defaults, which throw {@link UnsupportedOperationException}. That exception is a
 * <strong>test-configuration bug, never a skip</strong> — the scenarios needing connection control
 * are gated behind {@link Capability#STALE} and {@link Capability#UNAVAILABLE_INIT}, so reaching a
 * default means a capability was declared the backend cannot back up, and a silent no-op there
 * would report the scenario as passed.
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
     * Returns a short description of what is being controlled, for startup logging and for the
     * failure messages of unsupported operations.
     *
     * @return a human-readable description of this backend control
     */
    default String description() {
        return getClass().getSimpleName();
    }

    /**
     * Returns how the backend is driven, as one of the two kinds the provider contract recognises.
     *
     * <p>Appendix F requires the control to state this rather than the harness to infer it, and is
     * why there is deliberately no default: an omitted value would be an unfalsifiable claim rather
     * than no claim. Both implementations the TCK ships answer it, so the only author who has to is
     * the one writing a custom control — precisely the case where it cannot be inferred.
     *
     * @return which of the two control contracts this run is conducted under
     */
    ControlApi controlApi();

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
