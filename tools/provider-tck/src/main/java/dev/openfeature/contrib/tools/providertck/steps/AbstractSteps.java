package dev.openfeature.contrib.tools.providertck.steps;

import dev.openfeature.contrib.tools.providertck.BackendControl;
import dev.openfeature.contrib.tools.providertck.ProviderTckHarness;
import dev.openfeature.contrib.tools.providertck.TckRuntime;
import dev.openfeature.contrib.tools.providertck.TckState;

/**
 * Base for the TCK step definition classes.
 *
 * <p>Holds the PicoContainer-injected scenario state and gives subclasses the only two collaborators
 * a step is allowed to reach: the provider author's harness, and the {@link BackendControl} that
 * manipulates the backend.
 *
 * <p>Deliberately no accessor for the runtime itself. Steps must not know whether the backend is a
 * container reached over HTTP or a map in this JVM — that is exactly what {@link BackendControl}
 * exists to hide, and it is what lets the same Gherkin run in both modes.
 */
public abstract class AbstractSteps {

    /** Scenario-scoped state, shared across all step classes in a scenario. */
    protected final TckState state;

    protected AbstractSteps(TckState state) {
        this.state = state;
    }

    /**
     * Returns the provider author's harness.
     *
     * @return the discovered harness
     */
    protected ProviderTckHarness harness() {
        return TckRuntime.get().harness();
    }

    /**
     * Returns the seam through which the backend is manipulated.
     *
     * @return the backend control for this suite
     */
    protected BackendControl backend() {
        return TckRuntime.get().backendControl();
    }
}
