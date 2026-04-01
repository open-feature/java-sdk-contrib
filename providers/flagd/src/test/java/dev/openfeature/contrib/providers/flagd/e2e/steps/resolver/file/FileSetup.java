package dev.openfeature.contrib.providers.flagd.e2e.steps.resolver.file;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.e2e.State;
import io.cucumber.java.Before;

public class FileSetup {

    private final State state;

    public FileSetup(State state) {
        this.state = state;
    }

    @Before
    public void setup() {
        state.resolverType = Config.Resolver.FILE;
    }
}
