package dev.openfeature.contrib.tools.tck;

import java.time.Duration;

/**
 * The seam between the TCK's step definitions and whatever manipulates the backend.
 *
 * <p>Use {@link #overHttp} for a provider with a backend, or {@link #inProcess()} for one without.
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
     * @return an in-process control over a copy of the canonical flag set
     */
    static InProcessControl inProcess() {
        return new InProcessBackendControl();
    }

    /** Brings the backend to the baseline state before each scenario. */
    void prepareScenario();

    /** Mutates flag configuration so {@code changing-flag} resolves to a different value. */
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
     * Takes the backend down and lets it come back after {@code outage}, without stopping any
     * container.
     *
     * <p><strong>Returns once the outage has begun, and says nothing about when it ends.</strong>
     * {@code POST /restart} is specified to answer either when the backend is down or only once it is
     * back, so a scenario that wants the recovery asserts it through the provider's own events or
     * client state. Assuming this call waited makes a feature file pass against one control API and
     * hang against another.
     *
     * @param outage how long the backend stays unreachable
     * @throws UnsupportedOperationException if this backend has no connection to lose
     */
    default void disconnectFor(Duration outage) {
        throw unsupported("disconnectFor");
    }

    /**
     * Restarts the backend on one of its own named flag configurations.
     *
     * <p>The names belong to the backend, not to the TCK: the control API takes {@code config} as a
     * free string defaulting to {@code default}, so a feature file naming one is naming something
     * only that backend's control defines. Optional for the reason {@link #disconnect()} is -- a
     * control serving a single in-process flag set has no second configuration to offer -- and
     * therefore for extension scenarios only. The canonical scenarios never ask: the baseline every
     * control already serves is the configuration they are written against.
     *
     * <p>Takes effect before the provider connects, so a scenario states it ahead of
     * {@code a stable provider}. The next scenario starts from this suite's own configuration again.
     *
     * @param configuration the configuration name, as that backend's control defines it
     * @throws UnsupportedOperationException if this backend serves one configuration only
     */
    default void serveConfiguration(String configuration) {
        throw unsupported("serveConfiguration");
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
     * Returns what kind of backend this run drives; defaults to {@link BackendType#CUSTOM}.
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
                + "'; test-configuration bug, remove Capability.STALE and Capability.UNAVAILABLE_INIT.");
    }
}
