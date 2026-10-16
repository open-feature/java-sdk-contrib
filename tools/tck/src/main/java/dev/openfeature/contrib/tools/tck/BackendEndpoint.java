package dev.openfeature.contrib.tools.tck;

import org.testcontainers.containers.ComposeContainer;

/**
 * Addresses of the running backend stack, handed to
 * {@link ContainerizedProviderTckTest#createProvider(BackendEndpoint)} once host ports are known.
 */
public final class BackendEndpoint {

    private final ComposeContainer compose;
    private final String defaultService;

    BackendEndpoint(ComposeContainer compose, String defaultService) {
        this.compose = compose;
        this.defaultService = defaultService;
    }

    /**
     * Returns the host the stack is reachable on; not necessarily {@code localhost}.
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
     * Resolves the mapped host port for a container-internal port on the default backend service.
     *
     * @param internalPort the container-internal port, as declared by
     *     {@link ContainerizedProviderTckTest#backendPorts()}
     * @return the host port the service is reachable on
     */
    public int port(int internalPort) {
        return port(defaultService, internalPort);
    }

    /**
     * Resolves the mapped host port for a container-internal port on a named service; it must be
     * declared via {@link ContainerizedProviderTckTest#additionalPorts()}.
     *
     * @param service the Compose service name
     * @param internalPort the container-internal port
     * @return the host port the service is reachable on
     */
    public int port(String service, int internalPort) {
        return compose.getServicePort(service, internalPort);
    }
}
