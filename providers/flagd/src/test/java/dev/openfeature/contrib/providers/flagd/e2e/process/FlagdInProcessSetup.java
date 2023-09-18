package dev.openfeature.contrib.providers.flagd.e2e.process;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.steps.StepDefinitions;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.cucumber.java.BeforeAll;

public class FlagdInProcessSetup {
    
    @BeforeAll()
    public static void setup() throws InterruptedException {
        FlagdProvider provider = new FlagdProvider(FlagdOptions.builder()
        .resolverType(Config.Evaluator.IN_PROCESS)
        .host("localhost")
        .port(9090)
        .build());
        OpenFeatureAPI.getInstance().setProviderAndWait("process", provider);
        Client client = OpenFeatureAPI.getInstance().getClient("process");
        StepDefinitions.setClient(client);
    }
}
