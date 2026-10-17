package dev.openfeature.contrib.tools.tck;

/**
 * The seam between the TCK's step definitions and whatever manipulates the backend.
 *
 * <p>Obtained from {@link #overHttp} (usually via {@link ContainerizedProviderTckTest}) for a
 * provider with a backend, or {@link #inProcess()} for one with none. An implementation of its
 * own must still declare its {@link BackendType}, a closed set, so the conformance claim stays
 * valid. The connection operations default to throwing {@link UnsupportedOperationException}, a
 * test-configuration bug rather than a skip.
 *
 * @see Capability
 * @see ProviderTckTest
 */
public interface BackendControl {

    /**
     * Returns a control over the canonical control API, for a backend this suite did not start.
     *
     * @param baseUrl root of the control API, without a trailing slash
     * @param backendConfiguration the configuration to seed, or {@code null} for none
     * @return a control speaking the canonical control API
     */
    static BackendControl overHttp(String baseUrl, String backendConfiguration) {
        return new HttpBackendControl(baseUrl, backendConfiguration);
    }

    /**
     * Returns a control for a provider with no backend, which also supplies the provider.
     *
     * <p>Appendix F's narrow allowance; a provider that has a backend must use the control API.
     *
     * @return an in-process control over a copy of the canonical flag set
     */
    static InProcessControl inProcess() {
        return new InProcessBackendControl();
    }

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
     * Returns what kind of backend this run drives.
     *
     * <p>Defaults to {@link BackendType#CUSTOM}, which is the honest answer for a control the TCK
     * does not ship. One that drives the canonical control API should return
     * {@link BackendType#HTTP}.
     *
     * @return what kind of backend this run drives
     */
    default BackendType backendType() {
        return BackendType.CUSTOM;
    }

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
