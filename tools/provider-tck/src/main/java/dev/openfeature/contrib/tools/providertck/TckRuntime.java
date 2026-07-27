package dev.openfeature.contrib.tools.providertck;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Suite-scoped runtime: discovers the provider's harness, owns the Compose stack, and exposes the
 * control API client to the step definitions.
 *
 * <p>The Compose stack is started <strong>once</strong>, before the first scenario, and stopped
 * after the last one. It is never stopped or restarted in between. Testcontainers cannot reliably
 * preserve dynamically mapped host ports across a container restart, so a restart would silently
 * invalidate every provider already pointed at the old port. Backend unavailability is therefore
 * always simulated inside the running stack through the control API. See the normative statement of
 * this invariant in {@code openapi/control-api.yaml}.
 *
 * <p>State is static because Cucumber's {@code @BeforeAll} / {@code @AfterAll} hooks are static and
 * the stack must outlive individual scenarios. Consequently only one TCK suite may run per JVM fork
 * at a time.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "The harness and control API client are shared collaborators by design; "
                + "step definitions must act on the same instances the suite started")
public final class TckRuntime {

    private static final Logger log = LoggerFactory.getLogger(TckRuntime.class);

    /** System property selecting a harness by simple class name when several are registered. */
    public static final String HARNESS_SELECTOR_PROPERTY = "openfeature.tck.harness";

    private static TckRuntime instance;

    private final ProviderTckHarness harness;
    private final ComposeContainer compose;
    private final ControlApiClient controlApi;
    private final BackendEndpoint endpoint;

    private TckRuntime(ProviderTckHarness harness, ComposeContainer compose) {
        this.harness = harness;
        this.compose = compose;
        this.endpoint = new BackendEndpoint(compose, harness.backendService());
        String baseUrl = "http://" + compose.getServiceHost(harness.backendService(), null) + ":"
                + compose.getServicePort(harness.backendService(), harness.controlPort());
        this.controlApi = new ControlApiClient(baseUrl, harness.settleTime());
    }

    /**
     * Starts the Compose stack if it is not already running, and returns the shared runtime.
     *
     * @return the suite-scoped runtime
     */
    public static synchronized TckRuntime startIfNeeded() {
        if (instance == null) {
            ProviderTckHarness harness = discoverHarness();
            log.info("Provider TCK harness: {}", harness.getClass().getName());
            instance = new TckRuntime(harness, startCompose(harness));
            instance.controlApi.awaitReady(harness.startupTimeout());
            log.info("Control API ready at {}", instance.controlApi.baseUrl());
        }
        return instance;
    }

    /**
     * Stops the Compose stack and releases the shared runtime.
     */
    public static synchronized void stop() {
        if (instance != null) {
            instance.compose.stop();
            instance = null;
        }
    }

    /**
     * Returns the running runtime.
     *
     * @return the suite-scoped runtime
     * @throws IllegalStateException if the stack has not been started
     */
    public static synchronized TckRuntime get() {
        if (instance == null) {
            throw new IllegalStateException("TCK runtime has not been started");
        }
        return instance;
    }

    /**
     * Returns the provider author's harness.
     *
     * @return the discovered harness
     */
    public ProviderTckHarness harness() {
        return harness;
    }

    /**
     * Returns the client for the backend's control API.
     *
     * @return the control API client
     */
    public ControlApiClient controlApi() {
        return controlApi;
    }

    /**
     * Returns the host and mapped ports of the running stack.
     *
     * @return the backend endpoint
     */
    public BackendEndpoint endpoint() {
        return endpoint;
    }

    private static ComposeContainer startCompose(ProviderTckHarness harness) {
        File composeFile = harness.composeFile();
        if (!composeFile.isFile()) {
            throw new IllegalStateException("Compose file not found: " + composeFile.getAbsolutePath()
                    + ". ProviderTckHarness.composeFile() is resolved relative to the module directory.");
        }
        ComposeContainer compose = new ComposeContainer(composeFile);

        compose.withExposedService(harness.backendService(), harness.controlPort(), Wait.forListeningPort());
        for (Integer port : harness.backendPorts()) {
            compose.withExposedService(harness.backendService(), port, Wait.forListeningPort());
        }
        for (Map.Entry<String, List<Integer>> service :
                harness.additionalExposedPorts().entrySet()) {
            for (Integer port : service.getValue()) {
                compose.withExposedService(service.getKey(), port, Wait.forListeningPort());
            }
        }
        compose.withStartupTimeout(harness.startupTimeout());

        log.info("Starting Compose stack {} (started once per suite, never restarted)", composeFile.getAbsolutePath());
        compose.start();
        return compose;
    }

    /**
     * Finds the harness for the suite that is currently executing.
     *
     * <p>Primary mechanism: the executing suite class itself, reported by {@link TckSuiteListener}.
     * A suite class implements {@link ProviderTckHarness}, so a provider with several transports
     * writes one suite class per transport and needs no registration, no system property and no
     * build configuration to keep them apart.
     *
     * <p>Fallback: {@link java.util.ServiceLoader}, for setups where the launcher does not
     * auto-register listeners. That path cannot distinguish between several registered harnesses,
     * so it accepts exactly one unless {@link #HARNESS_SELECTOR_PROPERTY} names which to use.
     */
    private static ProviderTckHarness discoverHarness() {
        Optional<Class<? extends ProviderTckHarness>> suite = TckSuiteListener.currentSuite();
        if (suite.isPresent()) {
            return instantiate(suite.get());
        }

        List<ProviderTckHarness> found = new ArrayList<>();
        ServiceLoader.load(ProviderTckHarness.class).forEach(found::add);

        if (found.isEmpty()) {
            throw new IllegalStateException("No ProviderTckHarness found. Write a test class extending "
                    + "AbstractProviderTckTest; it is both the JUnit suite and the harness.");
        }
        if (found.size() == 1) {
            return found.get(0);
        }

        String selector = System.getProperty(HARNESS_SELECTOR_PROPERTY);
        if (selector == null) {
            throw new IllegalStateException("Several ProviderTckHarness implementations are registered ("
                    + found.stream().map(h -> h.getClass().getSimpleName()).collect(Collectors.joining(", "))
                    + ") and the executing suite could not be determined, which normally means the JUnit "
                    + "Platform did not auto-register TckSuiteListener. Select one with -D"
                    + HARNESS_SELECTOR_PROPERTY + "=<simple class name>.");
        }
        return found.stream()
                .filter(h -> h.getClass().getSimpleName().equals(selector))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No registered ProviderTckHarness named '" + selector
                        + "'. Registered: "
                        + found.stream().map(h -> h.getClass().getSimpleName()).collect(Collectors.joining(", "))));
    }

    private static ProviderTckHarness instantiate(Class<? extends ProviderTckHarness> suite) {
        try {
            return suite.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    suite.getName() + " could not be instantiated. A TCK suite class needs a public no-argument "
                            + "constructor, because the TCK creates one to read its configuration.",
                    e);
        }
    }
}
