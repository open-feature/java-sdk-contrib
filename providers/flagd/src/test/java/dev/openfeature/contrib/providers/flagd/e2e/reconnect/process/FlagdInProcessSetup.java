package dev.openfeature.contrib.providers.flagd.e2e.reconnect.process;

import dev.openfeature.contrib.providers.flagd.e2e.ContainerConfig;
import io.cucumber.java.AfterAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Isolated;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.reconnect.steps.StepDefinitions;
import dev.openfeature.sdk.FeatureProvider;
import io.cucumber.java.BeforeAll;
import org.testcontainers.containers.GenericContainer;

@Isolated()
@Order(value = Integer.MAX_VALUE)
public class FlagdInProcessSetup {

    private static final GenericContainer flagdContainer = ContainerConfig.sync(true);
    @BeforeAll()
    public static void setup() throws InterruptedException {
        flagdContainer.start();
        FeatureProvider workingProvider = new FlagdProvider(FlagdOptions.builder()
        .resolverType(Config.Resolver.IN_PROCESS)
        .port(flagdContainer.getFirstMappedPort())
        .build());
        StepDefinitions.setUnstableProvider(workingProvider);

        FeatureProvider unavailableProvider = new FlagdProvider(FlagdOptions.builder()
        .resolverType(Config.Resolver.IN_PROCESS)
        .deadline(100)
        .port(9092) // this port isn't serving anything, error expected
        .build());
        StepDefinitions.setUnavailableProvider(unavailableProvider);
    }

    @AfterAll
    public static void tearDown() {
        flagdContainer.stop();
    }
}
