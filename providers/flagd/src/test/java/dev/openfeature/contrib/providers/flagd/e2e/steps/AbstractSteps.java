package dev.openfeature.contrib.providers.flagd.e2e.steps;

import dev.openfeature.contrib.providers.flagd.e2e.State;

public abstract class AbstractSteps {
    protected State state;

    protected AbstractSteps(State state) {
        this.state = state;
    }
}
