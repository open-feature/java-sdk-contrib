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

    private final ProviderTckHarness harness;

    private final BackendControl backend;

    protected AbstractSteps(TckState state, ProviderTckHarness harness, BackendControl backend) {
        this.harness = harness;
        this.backend = backend;
        this.state = state;
    }

    /**
     * Returns the provider author's harness.
     *
     * @return the discovered harness
     */
    protected ProviderTckHarness harness() {
        return harness;
    }

    /**
     * Returns the seam through which the backend is manipulated.
     *
     * @return the backend control for this suite
     */
    protected BackendControl backend() {
        return backend;
    }
}
