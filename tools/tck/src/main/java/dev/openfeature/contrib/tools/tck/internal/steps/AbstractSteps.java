package dev.openfeature.contrib.tools.tck.internal.steps;

import dev.openfeature.contrib.tools.tck.BackendControl;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.contrib.tools.tck.ProviderTck;
import dev.openfeature.contrib.tools.tck.TckState;

/**
 * Base for TCK step definitions; exposes scenario state, the harness, and {@link BackendControl}.
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
        return ProviderTck.harness();
    }

    /**
     * Returns the seam through which the backend is manipulated.
     *
     * @return the backend control for this suite
     */
    protected BackendControl backend() {
        return ProviderTck.backendControl();
    }
}
