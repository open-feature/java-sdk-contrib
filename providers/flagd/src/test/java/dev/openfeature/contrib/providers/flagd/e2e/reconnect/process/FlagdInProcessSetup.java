package dev.openfeature.contrib.providers.flagd.e2e.reconnect.process;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Isolated;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.reconnect.steps.StepDefinitions;
import dev.openfeature.sdk.FeatureProvider;
import io.cucumber.java.BeforeAll;

@Isolated()
@Order(value = Integer.MAX_VALUE)
public class FlagdInProcessSetup {
    
    @BeforeAll()
    public static void setup() throws InterruptedException {
        FeatureProvider workingProvider = new FlagdProvider(FlagdOptions.builder()
        .resolverType(Config.Evaluator.IN_PROCESS)
        .deadline(3000)
        .port(9091)
        .build());
        StepDefinitions.setUnstableProvider(workingProvider);

        FeatureProvider unavailableProvider = new FlagdProvider(FlagdOptions.builder()
        .resolverType(Config.Evaluator.IN_PROCESS)
        .deadline(100)
        .port(9092) // this port isn't serving anything, error expected
        .build());
        StepDefinitions.setUnavailableProvider(unavailableProvider);
    }
}
