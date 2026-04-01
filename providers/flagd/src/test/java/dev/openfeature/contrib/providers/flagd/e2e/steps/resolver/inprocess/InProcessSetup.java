package dev.openfeature.contrib.providers.flagd.e2e.steps.resolver.inprocess;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.e2e.State;
import io.cucumber.java.Before;

public class InProcessSetup {

    private final State state;

    public InProcessSetup(State state) {
        this.state = state;
    }

    @Before
    public void setup() {
        state.resolverType = Config.Resolver.IN_PROCESS;
    }
}
