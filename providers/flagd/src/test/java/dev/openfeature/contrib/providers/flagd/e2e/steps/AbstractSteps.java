package dev.openfeature.contrib.providers.flagd.e2e.steps;

import dev.openfeature.contrib.providers.flagd.e2e.State;

abstract class AbstractSteps {
    State state;

    public AbstractSteps(State state) {
        this.state = state;
    }
}
