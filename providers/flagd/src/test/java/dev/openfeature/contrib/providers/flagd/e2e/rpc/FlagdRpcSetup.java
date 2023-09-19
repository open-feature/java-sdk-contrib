package dev.openfeature.contrib.providers.flagd.e2e.rpc;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.steps.StepDefinitions;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.cucumber.java.BeforeAll;


public class FlagdRpcSetup {

    @BeforeAll()
    public static void setup() {
        FlagdProvider provider = new FlagdProvider(FlagdOptions.builder()
        .resolverType(Config.Evaluator.RPC)
        // set a generous deadline, to prevent timeouts in actions
        .deadline(3000)
        .build());
        OpenFeatureAPI.getInstance().setProvider("rpc", provider);
        Client client = OpenFeatureAPI.getInstance().getClient("rpc");
        StepDefinitions.setClient(client);
    }
}
