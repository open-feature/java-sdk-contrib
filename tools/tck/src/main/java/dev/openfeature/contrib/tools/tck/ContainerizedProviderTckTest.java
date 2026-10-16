package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.FeatureProvider;
import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Base JUnit Platform Suite for providers that talk to an external backend.
 *
 * <p>Manages the Docker Compose lifecycle (started once, never restarted), port discovery and an
 * {@link HttpBackendControl}. See {@code tools/tck/README.md} for an example.
 *
 * @see ProviderTckTest
 * @see HttpBackendControl
 */
public abstract class ContainerizedProviderTckTest extends ProviderTckTest {

    private static final Logger log = LoggerFactory.getLogger(ContainerizedProviderTckTest.class);

    private ComposeContainer compose;
    private BackendEndpoint endpoint;
    private HttpBackendControl control;

    /**
     * Returns the Docker Compose file for the backend stack, relative to the module directory. The
     * stack must not pin host ports.
     *
     * @return the Compose file describing the backend stack
     */
    public abstract File composeFile();

    /**
     * Returns the container-internal ports on {@link #backendService()} the provider connects to.
     * {@link #controlPort()} is exposed automatically.
     *
     * @return container-internal ports the provider connects to
     */
    public abstract List<Integer> backendPorts();

    /**
     * Creates the provider under test, configured against the running backend. Do not call
     * {@code setProvider} or {@code initialize}; the TCK owns the lifecycle.
     *
     * @param endpoint host and mapped ports of the running backend stack
     * @return a configured, uninitialised provider
     */
    public abstract FeatureProvider createProvider(BackendEndpoint endpoint);

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link #createProvider(BackendEndpoint)} with the running stack's endpoint.
     */
    @Override
    public final FeatureProvider createProvider() {
        return createProvider(endpoint());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Abstract here; a provider with a real backend can always be pointed at a closed port.
     */
    @Override
    public abstract FeatureProvider createUnavailableProvider();

    /**
     * Returns the Compose service name hosting the control API and backend.
     *
     * @return the Compose service name, {@code backend} by default
     */
    public String backendService() {
        return "backend";
    }

    /**
     * Returns the container-internal port the control API listens on.
     *
     * @return the control API port, {@code 8080} by default
     */
    public int controlPort() {
        return 8080;
    }

    /**
     * Returns extra Compose services and their container-internal ports to expose; resolve them
     * with {@link BackendEndpoint#port(String, int)}.
     *
     * @return additional services and ports to expose, empty by default
     */
    public Map<String, List<Integer>> additionalPorts() {
        return Collections.emptyMap();
    }

    /**
     * Returns the backend configuration name passed to {@code POST /start?config=...}; distinct
     * from {@link ProviderTckHarness#configuration()}.
     *
     * @return the backend configuration name passed to {@code POST /start}, {@code default} by
     *     default
     */
    public String backendConfiguration() {
        return "default";
    }

    /**
     * Returns how long to wait for the Compose stack and its control API to become reachable.
     *
     * @return the stack startup timeout, 60 seconds by default
     */
    public Duration startupTimeout() {
        return Duration.ofSeconds(60);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Starts the Compose stack and waits up to {@link #startupTimeout()} for the control API.
     */
    @Override
    public final void startSuite() {
        compose = startCompose();
        endpoint = new BackendEndpoint(compose, backendService());
        control = new HttpBackendControl(
                "http://" + endpoint.host() + ":" + endpoint.port(controlPort()), backendConfiguration());
        control.awaitReady(startupTimeout());
        log.info("Control API ready at {}", control.baseUrl());
    }

    /** {@inheritDoc} */
    @Override
    public final void stopSuite() {
        if (compose != null) {
            compose.stop();
        }
        compose = null;
        endpoint = null;
        control = null;
    }

    /** {@inheritDoc} */
    @Override
    public final BackendControl backendControl() {
        return control;
    }

    /**
     * Returns the host and mapped ports of the running stack.
     *
     * @return the backend endpoint
     * @throws IllegalStateException if the stack has not been started
     */
    protected final BackendEndpoint endpoint() {
        if (endpoint == null) {
            throw new IllegalStateException("The Compose stack has not been started yet.");
        }
        return endpoint;
    }

    private ComposeContainer startCompose() {
        File composeFile = composeFile();
        if (!composeFile.isFile()) {
            throw new IllegalStateException("Compose file not found: " + composeFile.getAbsolutePath()
                    + ". ContainerizedProviderTckTest.composeFile() is resolved relative to the module directory.");
        }
        ComposeContainer stack = new ComposeContainer(composeFile);

        stack.withExposedService(backendService(), controlPort(), Wait.forListeningPort());
        for (Integer port : backendPorts()) {
            stack.withExposedService(backendService(), port, Wait.forListeningPort());
        }
        for (Map.Entry<String, List<Integer>> service : additionalPorts().entrySet()) {
            for (Integer port : service.getValue()) {
                stack.withExposedService(service.getKey(), port, Wait.forListeningPort());
            }
        }
        stack.withStartupTimeout(startupTimeout());

        log.info("Starting Compose stack {} (started once per suite, never restarted)", composeFile.getAbsolutePath());
        stack.start();
        return stack;
    }
}
