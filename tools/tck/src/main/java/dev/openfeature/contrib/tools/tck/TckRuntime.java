package dev.openfeature.contrib.tools.tck;

import java.net.URI;
import java.util.Collection;
import dev.openfeature.contrib.tools.tck.internal.TckSuiteListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Suite-scoped runtime: discovers the harness, drives its suite lifecycle once, and exposes its
 * {@link BackendControl} to step definitions.
 *
 * <p>State is static (Cucumber's {@code @BeforeAll}/{@code @AfterAll} are static), so only one TCK
 * suite may run per JVM fork at a time.
 */
public final class TckRuntime {

    private static final Logger log = LoggerFactory.getLogger(TckRuntime.class);

    /** System property selecting a harness by simple class name when several are registered. */
    public static final String HARNESS_SELECTOR_PROPERTY = "openfeature.tck.harness";

    private static TckRuntime instance;

    private final ProviderTckHarness harness;
    private final BackendControl backendControl;

    private TckRuntime(ProviderTckHarness harness, BackendControl backendControl) {
        this.harness = harness;
        this.backendControl = backendControl;
    }

    /**
     * Starts the suite lifecycle if it is not already running, and returns the shared runtime.
     *
     * @return the suite-scoped runtime
     */
    public static synchronized TckRuntime startIfNeeded() {
        if (instance != null) {
            return instance;
        }
        ProviderTckHarness harness = discoverHarness();
        log.info("Provider TCK harness: {}", harness.getClass().getName());

        // fail fast, before starting Docker
        Capability.requireDeclarable(harness.capabilities());

        try {
            harness.startSuite();
            BackendControl control = harness.backendControl();
            if (control == null) {
                throw new IllegalStateException(harness.getClass().getName()
                        + ".backendControl() returned null; supply BackendControl.overHttp(..) or BackendControl.inProcess().");
            }
            log.info("Backend control: {}", control.description());
            instance = new TckRuntime(harness, control);
        } catch (RuntimeException e) {
            // release anything a partial start allocated, e.g. containers
            harness.stopSuite();
            throw e;
        }
        return instance;
    }

    /**
     * Runs the harness's suite teardown and releases the shared runtime.
     */
    public static synchronized void stop() {
        if (instance != null) {
            ProviderTckHarness harness = instance.harness;
            instance = null;
            harness.stopSuite();
        }
    }

    /**
     * Returns the running runtime.
     *
     * @return the suite-scoped runtime
     * @throws IllegalStateException if the suite has not been started
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
    /**
     * Applies the capability gate to a scenario about to run.
     *
     * <p>The seam the step definitions call, which keeps {@code CapabilityGate} out of the API.
     *
     * @param source the scenario's feature file, or {@code null} when unknown
     * @param tags the scenario's Gherkin tags, including the leading at-sign
     */
    public static void gateScenario(URI source, Collection<String> tags) {
        CapabilityGate.requireDeclared(source, tags, get().harness().capabilities());
    }

    public ProviderTckHarness harness() {
        return harness;
    }

    /**
     * Returns the seam through which the TCK manipulates the backend.
     *
     * @return the backend control for this suite
     */
    public BackendControl backendControl() {
        return backendControl;
    }

    /**
     * Finds the harness for the executing suite via {@link TckSuiteListener}, falling back to
     * {@link java.util.ServiceLoader} (disambiguated by {@link #HARNESS_SELECTOR_PROPERTY}).
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
                    + "ContainerizedProviderTckTest (external backend) or ProviderTckTest (no backend); "
                    + "it is both the JUnit suite and the harness.");
        }
        if (found.size() == 1) {
            return found.get(0);
        }

        String selector = System.getProperty(HARNESS_SELECTOR_PROPERTY);
        if (selector == null) {
            throw new IllegalStateException("Several ProviderTckHarness implementations are registered ("
                    + found.stream().map(h -> h.getClass().getSimpleName()).collect(Collectors.joining(", "))
                    + ") and the executing suite is unknown; select one with -D"
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
                    suite.getName() + " could not be instantiated; it needs a public no-arg constructor.", e);
        }
    }
}
