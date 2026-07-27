package dev.openfeature.contrib.tools.providertck.steps;

import dev.openfeature.contrib.tools.providertck.ProviderTckHarness;
import dev.openfeature.contrib.tools.providertck.TckRuntime;
import dev.openfeature.contrib.tools.providertck.TckState;

/**
 * Base for the TCK step definition classes.
 *
 * <p>Holds the PicoContainer-injected scenario state and gives subclasses convenience access to the
 * suite-scoped runtime.
 */
public abstract class AbstractSteps {

    /** Scenario-scoped state, shared across all step classes in a scenario. */
    protected final TckState state;

    protected AbstractSteps(TckState state) {
        this.state = state;
    }

    /**
     * Returns the suite-scoped runtime that owns the Compose stack and control API.
     *
     * @return the running TCK runtime
     */
    protected TckRuntime runtime() {
        return TckRuntime.get();
    }

    /**
     * Returns the provider author's harness.
     *
     * @return the discovered harness
     */
    protected ProviderTckHarness harness() {
        return TckRuntime.get().harness();
    }
}
