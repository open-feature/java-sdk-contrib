package dev.openfeature.contrib.tools.tck.internal.steps;

import dev.openfeature.contrib.tools.tck.BackendControl;
import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.contrib.tools.tck.internal.TckSuiteListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The suite lifecycle: discovers the harness, starts it once, holds it for the scenarios.
 *
 * <p>Static because Cucumber's suite hooks are, so one suite runs per JVM fork at a time.
 */
final class TckLifecycle {

    /** System property selecting a harness by simple class name when several are registered. */
    static final String HARNESS_SELECTOR_PROPERTY = "openfeature.tck.harness";

    private static final Logger log = LoggerFactory.getLogger(TckLifecycle.class);

    private static TckLifecycle instance;

    private final ProviderTckHarness harness;
    private final BackendControl backendControl;

    private TckLifecycle(ProviderTckHarness harness, BackendControl backendControl) {
        this.harness = harness;
        this.backendControl = backendControl;
    }

    /** Starts the suite lifecycle if it is not already running. */
    static synchronized void startIfNeeded() {
        if (instance != null) {
            return;
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
            instance = new TckLifecycle(harness, control);
        } catch (RuntimeException e) {
            // release anything a partial start allocated, e.g. containers
            harness.stopSuite();
            throw e;
        }
    }

    /** Runs the harness's suite teardown and releases the runtime. */
    static synchronized void stop() {
        if (instance != null) {
            ProviderTckHarness harness = instance.harness;
            instance = null;
            harness.stopSuite();
        }
    }

    /** Whether the suite has started; glue can be launched without one. */
    static synchronized boolean isRunning() {
        return instance != null;
    }

    /** The discovered harness. */
    static ProviderTckHarness harness() {
        return current().harness;
    }

    /** The seam through which the TCK manipulates the backend. */
    static BackendControl backendControl() {
        return current().backendControl;
    }

    private static synchronized TckLifecycle current() {
        if (instance == null) {
            throw new IllegalStateException("the TCK suite has not been started");
        }
        return instance;
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
