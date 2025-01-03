package dev.openfeature.contrib.providers.flagd.e2e.rpc;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.ContainerConfig;
import dev.openfeature.contrib.providers.flagd.e2e.steps.StepDefinitions;
import dev.openfeature.sdk.FeatureProvider;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Isolated;
import org.testcontainers.containers.GenericContainer;

@Isolated()
@Order(value = Integer.MAX_VALUE)
public class FlagdRpcSetup {

    private static FeatureProvider provider;
    private static final GenericContainer flagdContainer = ContainerConfig.flagd();

    @BeforeAll()
    public static void setup() {
        flagdContainer.start();
    }

    @Before()
    public static void test_setup() {
        FlagdRpcSetup.provider = new FlagdProvider(FlagdOptions.builder()
                .resolverType(Config.Resolver.RPC)
                .port(flagdContainer.getFirstMappedPort())
                .deadline(500)
                .build());
        StepDefinitions.setProvider(provider);
    }

    @AfterAll
    public static void tearDown() {
        flagdContainer.stop();
    }
}
