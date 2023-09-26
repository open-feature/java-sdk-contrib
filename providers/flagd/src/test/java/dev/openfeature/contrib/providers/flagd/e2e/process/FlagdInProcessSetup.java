package dev.openfeature.contrib.providers.flagd.e2e.process;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Isolated;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.steps.StepDefinitions;
import dev.openfeature.sdk.FeatureProvider;
import io.cucumber.java.BeforeAll;

@Isolated()
@Order(value = Integer.MAX_VALUE)
public class FlagdInProcessSetup {

    private static FeatureProvider provider;
    
    @BeforeAll()
    public static void setup() throws InterruptedException {
        FlagdInProcessSetup.provider = new FlagdProvider(FlagdOptions.builder()
        .resolverType(Config.Evaluator.IN_PROCESS)
        .deadline(3000)
        .host("localhost")
        .port(9090)
        .build());
        StepDefinitions.setProvider(provider);
    }
}
