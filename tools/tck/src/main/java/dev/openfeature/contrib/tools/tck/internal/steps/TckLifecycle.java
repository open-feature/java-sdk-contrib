package dev.openfeature.contrib.tools.tck.internal.steps;

import dev.openfeature.contrib.tools.tck.BackendControl;
import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.contrib.tools.tck.internal.TckSuiteListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One harness and the backend it brings up, owned by whoever started it.
 *
 * <p>An instance per {@link TckObjectFactory}, which Cucumber creates once per run when scenarios
 * are sequential and once per thread when they are not -- so enabling parallel execution gives each
 * thread a backend of its own rather than making them share one.
 *
 * <p>Two statics remain, and only because Cucumber's suite hooks are static: a flag saying a suite
 * is running, so glue launched without one registers nothing, and a registry of what was started,
 * so {@code @AfterAll} can stop the instances other threads own. Neither holds a harness.
 */
final class TckLifecycle {

    /** System property selecting a harness by simple class name when several are registered. */
    static final String HARNESS_SELECTOR_PROPERTY = "openfeature.tck.harness";

    private static final Logger log = LoggerFactory.getLogger(TckLifecycle.class);

    private static final Set<TckLifecycle> started = Collections.synchronizedSet(new LinkedHashSet<>());

    private static volatile boolean suiteRunning;

    private final ProviderTckHarness harness;
    private final BackendControl backendControl;

    private TckLifecycle(ProviderTckHarness harness, BackendControl backendControl) {
        this.harness = harness;
        this.backendControl = backendControl;
    }

    /**
     * Marks a suite as running and rejects an undeclarable capability set before anything starts.
     *
     * <p>The check is here rather than in {@link #start} so a misdeclared suite fails once, cleanly,
     * instead of once per thread inside the first scenario each one runs.
     */
    static void beginSuite() {
        ProviderTckHarness harness = discoverHarness();
        log.info("Provider TCK harness: {}", harness.getClass().getName());
        Capability.requireDeclarable(harness.capabilities());
        suiteRunning = true;
    }

    /** Stops every lifecycle this suite started, on whichever thread started it. */
    static void endSuite() {
        suiteRunning = false;
        List<TckLifecycle> toStop;
        synchronized (started) {
            toStop = new ArrayList<>(started);
            started.clear();
        }
        RuntimeException failure = null;
        for (TckLifecycle lifecycle : toStop) {
            try {
                lifecycle.harness.stopSuite();
            } catch (RuntimeException e) {
                // keep stopping the rest; a leaked container is worse than a lost stack trace
                log.warn("Suite teardown failed for {}", lifecycle.harness.getClass().getName(), e);
                failure = failure == null ? e : failure;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Starts a lifecycle for the caller to own, or empty when no suite is running.
     *
     * @return a started lifecycle, or empty when there is no suite to start one for
     */
    static Optional<TckLifecycle> startForExecutingSuite() {
        if (!suiteRunning) {
            return Optional.empty();
        }
        return Optional.of(start(discoverHarness()));
    }

    /** Starts the given harness and registers the result for {@link #endSuite()}. */
    static TckLifecycle start(ProviderTckHarness harness) {
        try {
            harness.startSuite();
            BackendControl control = harness.backendControl();
            if (control == null) {
                throw new IllegalStateException(harness.getClass().getName()
                        + ".backendControl() returned null; supply BackendControl.overHttp(..) or BackendControl.inProcess().");
            }
            log.info("Backend control: {}", control.description());
            TckLifecycle lifecycle = new TckLifecycle(harness, control);
            started.add(lifecycle);
            return lifecycle;
        } catch (RuntimeException e) {
            // release anything a partial start allocated, e.g. containers
            harness.stopSuite();
            throw e;
        }
    }

    /** How many lifecycles are currently started, for tests that assert on isolation. */
    static int startedCount() {
        return started.size();
    }

    /** The harness this lifecycle owns. */
    ProviderTckHarness harness() {
        return harness;
    }

    /** The seam through which the TCK manipulates this lifecycle's backend. */
    BackendControl backendControl() {
        return backendControl;
    }

    /**
     * Finds the harness for the executing suite via {@link TckSuiteListener}, falling back to
     * {@link java.util.ServiceLoader} (disambiguated by {@link #HARNESS_SELECTOR_PROPERTY}). A fresh
     * instance each call, so two lifecycles never share one.
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
