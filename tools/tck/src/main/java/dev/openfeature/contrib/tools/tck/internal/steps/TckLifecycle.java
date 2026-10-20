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
 * One harness and the backend it brings up, owned by a {@link TckObjectFactory}.
 *
 * <p>Statics exist only because Cucumber's suite hooks are static.
 */
final class TckLifecycle {

    /** System property selecting a harness by simple class name when several are registered. */
    static final String HARNESS_SELECTOR_PROPERTY = "openfeature.tck.harness";

    private static final Logger log = LoggerFactory.getLogger(TckLifecycle.class);

    private static final Set<TckLifecycle> started = Collections.synchronizedSet(new LinkedHashSet<>());

    private static volatile boolean suiteRunning;

    private static volatile RuntimeException startFailure;

    private final ProviderTckHarness harness;
    private final BackendControl backendControl;

    private TckLifecycle(ProviderTckHarness harness, BackendControl backendControl) {
        this.harness = harness;
        this.backendControl = backendControl;
    }

    /** Marks a suite as running and rejects an undeclarable capability set before anything starts. */
    static void beginSuite() {
        ProviderTckHarness harness = discoverHarness();
        log.info("Provider TCK harness: {}", harness.getClass().getName());
        Capability.requireDeclarable(harness.capabilities());
        startFailure = null;
        suiteRunning = true;
    }

    /** Stops every lifecycle this suite started. */
    static void endSuite() {
        suiteRunning = false;
        startFailure = null;
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
                // keep stopping the rest
                log.warn(
                        "Suite teardown failed for {}",
                        lifecycle.harness.getClass().getName(),
                        e);
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
        RuntimeException failed = startFailure;
        if (failed != null) {
            // one Compose timeout per suite, not one per scenario
            throw new IllegalStateException("the suite's backend already failed to start", failed);
        }
        try {
            return Optional.of(start(discoverHarness()));
        } catch (RuntimeException e) {
            startFailure = e;
            throw e;
        }
    }

    /** Starts the given harness and registers the result for {@link #endSuite()}. */
    static TckLifecycle start(ProviderTckHarness harness) {
        try {
            harness.startSuite();
            BackendControl control = harness.backendControl();
            if (control == null) {
                throw new IllegalStateException(harness.getClass().getName()
                        + ".backendControl() returned null; supply overHttp(..) or inProcess().");
            }
            log.info("Backend control: {}", control.description());
            TckLifecycle lifecycle = new TckLifecycle(harness, control);
            started.add(lifecycle);
            return lifecycle;
        } catch (RuntimeException e) {
            // release anything a partial start allocated, e.g. containers, without losing why
            try {
                harness.stopSuite();
            } catch (RuntimeException cleanup) {
                e.addSuppressed(cleanup);
            }
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

    /** Finds a fresh harness for the executing suite, falling back to {@link ServiceLoader}. */
    private static ProviderTckHarness discoverHarness() {
        Optional<Class<? extends ProviderTckHarness>> suite = TckSuiteListener.currentSuite();
        if (suite.isPresent()) {
            return instantiate(suite.get());
        }

        List<ProviderTckHarness> found = new ArrayList<>();
        ServiceLoader.load(ProviderTckHarness.class).forEach(found::add);

        if (found.isEmpty()) {
            throw new IllegalStateException(
                    "No ProviderTckHarness found; extend ContainerizedProviderTckTest or ProviderTckTest.");
        }
        if (found.size() == 1) {
            return found.get(0);
        }

        String selector = System.getProperty(HARNESS_SELECTOR_PROPERTY);
        if (selector == null) {
            throw new IllegalStateException("Several ProviderTckHarness implementations are registered ("
                    + found.stream().map(h -> h.getClass().getSimpleName()).collect(Collectors.joining(", "))
                    + "); select one with -D" + HARNESS_SELECTOR_PROPERTY + "=<simple class name>.");
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
