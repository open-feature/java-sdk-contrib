package dev.openfeature.contrib.tools.providertck;

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
 * Base JUnit Platform Suite for providers that talk to an <strong>external backend</strong>.
 *
 * <p>Adds everything {@link ProviderTckTest} deliberately leaves out: the Docker Compose lifecycle,
 * discovery of dynamically mapped host ports, and construction of an {@link HttpBackendControl}
 * against the backend's control API. This is the base class for the overwhelming majority of
 * providers.
 *
 * <p>The HTTP control API described in {@code openapi/control-api.yaml} is the normative contract
 * here, and that is the point: another language's TCK drives the same endpoints against the same
 * stack and must get the same answers. Substituting a custom in-JVM {@link BackendControl} that
 * manipulates an external backend through a side channel bypasses that contract — see
 * {@link BackendControl} for why that is not an acceptable adoption path.
 *
 * <p>Provider authors implement three methods, optionally a fourth, and override the defaults their
 * stack needs. The Compose stack is started <strong>once</strong>, before the first scenario, and
 * stopped after the last one. It is never stopped or restarted in between: Testcontainers cannot
 * reliably preserve dynamically mapped host ports across a container restart, so a restart would
 * silently invalidate every provider already pointed at the old port. Backend unavailability is
 * therefore always simulated inside the running stack through the control API.
 *
 * <p>Example — the entire adoption for a provider with one transport:
 *
 * <pre>{@code
 * public class MyProviderTckTest extends ContainerizedProviderTckTest {
 *
 *     @Override
 *     public File composeFile() {
 *         return new File("src/test/resources/tck/docker-compose.yaml");
 *     }
 *
 *     @Override
 *     public List<Integer> backendPorts() {
 *         return Collections.singletonList(8013);
 *     }
 *
 *     @Override
 *     public FeatureProvider createProvider(BackendEndpoint endpoint) {
 *         return new MyProvider(endpoint.host(), endpoint.port(8013));
 *     }
 *
 *     @Override
 *     public FeatureProvider createUnavailableProvider() {
 *         return new MyProvider("localhost", 9999);
 *     }
 * }
 * }</pre>
 *
 * @see ProviderTckTest
 * @see HttpBackendControl
 */
public abstract class ContainerizedProviderTckTest extends ProviderTckTest {

    private static final Logger log = LoggerFactory.getLogger(ContainerizedProviderTckTest.class);

    private ComposeContainer compose;
    private BackendEndpoint endpoint;
    private HttpBackendControl control;

    // ---------------------------------------------------------------------------------------
    // What a provider author supplies
    // ---------------------------------------------------------------------------------------

    /**
     * Returns the Docker Compose file describing the backend stack under test.
     *
     * <p>The path is resolved relative to the Maven module directory, so
     * {@code new File("src/test/resources/tck/docker-compose.yaml")} is the idiomatic form.
     *
     * <p>The stack must not pin host ports — Docker assigns them dynamically and the TCK discovers
     * them after startup.
     *
     * @return the Compose file describing the backend stack
     */
    public abstract File composeFile();

    /**
     * Returns the container-internal ports on {@link #backendService()} that the provider connects
     * to, so Testcontainers can expose and map them.
     *
     * <p>The control API port from {@link #controlPort()} is exposed automatically and does not
     * need to be listed here.
     *
     * @return container-internal ports the provider connects to
     */
    public abstract List<Integer> backendPorts();

    /**
     * Creates the provider under test, configured against the running backend.
     *
     * <p>Called after the Compose stack is up and the control API has seeded the canonical flag
     * set. The endpoint carries the dynamically mapped host ports, which is why this is a factory
     * rather than a field: the ports do not exist until the stack has started.
     *
     * <p>The TCK owns the provider lifecycle from here. Do not call {@code setProvider} or
     * {@code initialize} yourself.
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
     * <p>Abstract here rather than defaulted: a provider with a real backend can always be pointed
     * at a closed port, so there is no reason for one not to cover the initialisation-failure
     * scenarios.
     */
    @Override
    public abstract FeatureProvider createUnavailableProvider();

    /**
     * Returns the Compose service name that hosts the control API and the backend the provider
     * connects to.
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
     * Returns extra services and container-internal ports to expose, for stacks that contain more
     * than the backend service.
     *
     * <p>Keys are Compose service names, values are container-internal ports. Resolve the mapped
     * ports with {@link BackendEndpoint#port(String, int)}.
     *
     * @return additional services and ports to expose, empty by default
     */
    public Map<String, List<Integer>> additionalExposedPorts() {
        return Collections.emptyMap();
    }

    /**
     * Returns the control API configuration name used to seed the canonical flag set.
     *
     * @return the configuration name passed to {@code POST /start}, {@code default} by default
     */
    public String defaultConfig() {
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
     * Returns how long to pause after a control API call before continuing.
     *
     * <p>Covers the gap between the control API acknowledging a command and the backend actually
     * having acted on it. Raise it if you see flakiness immediately after
     * {@code the flag was modified} or a provider setup step.
     *
     * @return the settle time, 50 milliseconds by default
     */
    public Duration settleTime() {
        return Duration.ofMillis(50);
    }

    // ---------------------------------------------------------------------------------------
    // The lifecycle-agnostic contract, implemented in terms of the Compose stack
    // ---------------------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Starts the Compose stack, resolves the control API's mapped port and waits for it to
     * accept commands.
     */
    @Override
    public final void startSuite() {
        compose = startCompose();
        endpoint = new BackendEndpoint(compose, backendService());
        control = new HttpBackendControl(
                "http://" + endpoint.host() + ":" + endpoint.port(controlPort()), defaultConfig(), settleTime());
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
        for (Map.Entry<String, List<Integer>> service : additionalExposedPorts().entrySet()) {
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
