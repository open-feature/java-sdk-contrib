package dev.openfeature.contrib.providers.flagd.e2e.steps.resolver.rpc;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.e2e.State;
import io.cucumber.java.Before;

public class RpcSetup {

    private final State state;

    public RpcSetup(State state) {
        this.state = state;
    }

    @Before
    public void setup() {
        state.resolverType = Config.Resolver.RPC;
    }
}
