package dev.openfeature.contrib.tools.tck;

/**
 * The seam between the TCK's step definitions and whatever manipulates the backend.
 *
 * <p>Use {@link HttpBackendControl} (via {@link ContainerizedProviderTckTest}) for providers with a
 * backend; {@link InProcessBackendControl} only for providers with none. The connection operations
 * default to throwing {@link UnsupportedOperationException}, a test-configuration bug rather than a
 * skip.
 *
 * @see Capability
 * @see ProviderTckTest
 */
public interface BackendControl {

    /**
     * Brings the backend to the baseline state: reachable, with the canonical flag set. Called before
     * each scenario.
     */
    void prepareScenario();

    /**
     * Mutates flag configuration so {@code changing-flag} resolves to a different value afterwards.
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
     * Makes the backend reachable again after {@link #disconnect()}, preserving flag state.
     *
     * @throws UnsupportedOperationException if this backend has no connection to restore
     */
    default void reconnect() {
        throw unsupported("reconnect");
    }

    /**
     * Returns a short description of what is being controlled, for logs and failure messages.
     *
     * @return a human-readable description of this backend control
     */
    default String description() {
        return getClass().getSimpleName();
    }

    /**
     * Returns how the backend is driven.
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
                + "'; test-configuration bug: remove Capability.STALE and Capability.UNAVAILABLE_INIT "
                + "from the harness, or supply a BackendControl that implements them.");
    }
}
