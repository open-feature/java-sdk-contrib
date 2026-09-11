package dev.openfeature.contrib.tools.tck;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Suite-scoped runtime: discovers the provider's harness, drives its suite lifecycle, and exposes
 * its {@link BackendControl} to the step definitions.
 *
 * <p>This class knows nothing about containers, ports or transports. Whatever must exist before the
 * first scenario is created by {@link ProviderTckHarness#startSuite()} and released by
 * {@link ProviderTckHarness#stopSuite()} — a Compose stack for
 * {@link ContainerizedProviderTckTest}, nothing at all for a harness whose backend is a data
 * structure in this JVM.
 *
 * <p>The lifecycle runs <strong>once</strong>: started before the first scenario, stopped after the
 * last one, never cycled in between. Scenario isolation is achieved through
 * {@link BackendControl#prepareScenario()} instead.
 *
 * <p>State is static because Cucumber's {@code @BeforeAll} / {@code @AfterAll} hooks are static and
 * the runtime must outlive individual scenarios. Consequently only one TCK suite may run per JVM
 * fork at a time.
 */
public final class TckRuntime {

    private static final Logger log = LoggerFactory.getLogger(TckRuntime.class);

    /** System property selecting a harness by simple class name when several are registered. */
    public static final String HARNESS_SELECTOR_PROPERTY = "openfeature.tck.harness";

    private static TckRuntime instance;

    /**
     * What the most recent suite was, kept after {@link #stop()} for the conformance report.
     *
     * <p>Cucumber emits the end-of-run event that writes the report <em>after</em> {@code @AfterAll}
     * has stopped the runtime, so the report would otherwise have nothing left to describe.
     */
    private static volatile TckRunMetadata lastRunMetadata;

    private final ProviderTckHarness harness;
    private final BackendControl backendControl;
    private final TckRunMetadata metadata;

    private TckRuntime(ProviderTckHarness harness, BackendControl backendControl) {
        this.harness = harness;
        this.backendControl = backendControl;
        this.metadata = new TckRunMetadata(
                harness.configuration(),
                harness.capabilities(),
                backendControl.description(),
                backendControl.controlApi(),
                harness.knownDeviations());
        recordRun(this.metadata);
    }

    /**
     * Records what the current run is, for the conformance report to describe afterwards.
     *
     * <p>A method rather than a field assignment because it is also the seam the report tests use:
     * they drive a real Cucumber run to check what the results stream says, and that needs a run to
     * describe without needing a backend to describe it.
     *
     * @param metadata what the run is, or {@code null} to forget the last one
     */
    static void recordRun(TckRunMetadata metadata) {
        lastRunMetadata = metadata;
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

        // Checked before the suite lifecycle starts, rather than only where the declaration
        // reaches the report: an adopter should not wait for Docker to be told about a
        // one-line mistake in capabilities().
        Capability.requireDeclarable(harness.capabilities());

        harness.startSuite();
        try {
            BackendControl control = harness.backendControl();
            if (control == null) {
                throw new IllegalStateException(harness.getClass().getName()
                        + ".backendControl() returned null. Every harness must supply the seam through "
                        + "which the TCK manipulates the backend — HttpBackendControl for an external "
                        + "backend, InProcessBackendControl for a provider that has none.");
            }
            log.info("Backend control: {}", control.description());
            instance = new TckRuntime(harness, control);
        } catch (RuntimeException e) {
            // startSuite() may have allocated a container stack before this failed.
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
     * Records what the provider under test calls itself.
     *
     * <p>A conformance report identifies the provider by its own metadata name rather than by the
     * suite's, and only a scenario that has built one can say what that is.
     *
     * @param name the provider's metadata name
     */
    public void recordProviderName(String name) {
        metadata.recordProviderName(name);
    }

    /**
     * Returns what was observed about the most recent suite, for the conformance report.
     *
     * @return the metadata of the last suite to start, or empty if none has
     */
    static Optional<TckRunMetadata> lastRunMetadata() {
        return Optional.ofNullable(lastRunMetadata);
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
