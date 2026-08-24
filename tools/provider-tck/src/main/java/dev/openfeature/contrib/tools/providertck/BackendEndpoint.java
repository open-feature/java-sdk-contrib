package dev.openfeature.contrib.tools.providertck;

import org.testcontainers.containers.ComposeContainer;

/**
 * Addresses of the running backend stack, handed to
 * {@link ContainerizedProviderTckTest#createProvider(BackendEndpoint)}.
 *
 * <p>This type exists because external ports are only known <em>after</em> the Compose stack has
 * started. Compose stacks under test must not pin host ports — Docker assigns them dynamically, so
 * a provider cannot be configured until the stack is up. That is the whole reason the harness
 * exposes a factory method rather than a pre-built provider instance.
 *
 * <p>The port mapping is stable for the lifetime of the suite: the stack is started once and never
 * restarted, so a provider built from this endpoint stays valid across every scenario. See the
 * no-container-restart invariant in {@code openapi/control-api.yaml}.
 */
public final class BackendEndpoint {

    private final ComposeContainer compose;
    private final String defaultService;

    BackendEndpoint(ComposeContainer compose, String defaultService) {
        this.compose = compose;
        this.defaultService = defaultService;
    }

    /**
     * Returns the host the stack is reachable on.
     *
     * <p>This is not necessarily {@code localhost}: with a remote Docker daemon, Docker Desktop on
     * some platforms, or a rootless setup, it can be an arbitrary address. Always use this value
     * rather than hard-coding a host.
     *
     * @return the Docker host serving the backend stack
     */
    public String host() {
        return compose.getServiceHost(defaultService, null);
    }

    /**
     * Returns the host the named service is reachable on.
     *
     * @param service the Compose service name
     * @return the Docker host serving that service
     */
    public String host(String service) {
        return compose.getServiceHost(service, null);
    }

    /**
     * Resolves the dynamically mapped host port for a container-internal port on the default
     * backend service.
     *
     * @param internalPort the container-internal port, as declared by
     *     {@link ContainerizedProviderTckTest#backendPorts()}
     * @return the host port the service is reachable on
     */
    public int port(int internalPort) {
        return port(defaultService, internalPort);
    }

    /**
     * Resolves the dynamically mapped host port for a container-internal port on a named service.
     *
     * <p>Use this for multi-service stacks — a proxy, an edge service, a sidecar. The service and
     * port must have been declared via {@link ContainerizedProviderTckTest#additionalExposedPorts()},
     * otherwise Testcontainers has not exposed it and this call fails.
     *
     * @param service the Compose service name
     * @param internalPort the container-internal port
     * @return the host port the service is reachable on
     */
    public int port(String service, int internalPort) {
        return compose.getServicePort(service, internalPort);
    }
}
