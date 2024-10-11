package dev.openfeature.contrib.providers.flagd.e2e.process;

import dev.openfeature.contrib.providers.flagd.e2e.ContainerConfig;
import io.cucumber.java.AfterAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Isolated;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.steps.StepDefinitions;
import dev.openfeature.sdk.FeatureProvider;
import io.cucumber.java.BeforeAll;
import org.testcontainers.containers.GenericContainer;

@Isolated()
@Order(value = Integer.MAX_VALUE)
public class FlagdInProcessSetup {

    private static FeatureProvider provider;

    private static final GenericContainer flagdContainer = ContainerConfig.sync();

    @BeforeAll()
    public static void setup() throws InterruptedException {
        flagdContainer.start();
        FlagdInProcessSetup.provider = new FlagdProvider(FlagdOptions.builder()
        .resolverType(Config.Resolver.IN_PROCESS)
        .deadline(1000)
        .streamDeadlineMs(0) // this makes reconnect tests more predictable
        .port(flagdContainer.getFirstMappedPort())
        .build());
        StepDefinitions.setProvider(provider);
    }

    @AfterAll
    public static void tearDown() {
        flagdContainer.stop();
    }
}
