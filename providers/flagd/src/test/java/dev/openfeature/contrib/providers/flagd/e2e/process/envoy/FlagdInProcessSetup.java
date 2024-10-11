package dev.openfeature.contrib.providers.flagd.e2e.process.envoy;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.ContainerConfig;
import dev.openfeature.contrib.providers.flagd.e2e.steps.StepDefinitions;
import dev.openfeature.sdk.FeatureProvider;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Isolated;
import org.testcontainers.containers.GenericContainer;

@Isolated()
@Order(value = Integer.MAX_VALUE)
public class FlagdInProcessSetup {

    private static FeatureProvider provider;

    private static final GenericContainer flagdContainer = ContainerConfig.sync(false, true);
    private static final GenericContainer envoyContainer = ContainerConfig.envoy();

    @BeforeAll()
    public static void setup() throws InterruptedException {
        flagdContainer.start();
        envoyContainer.start();
        final String targetUri = String.format("envoy://localhost:%s/flagd-sync.service",
                envoyContainer.getFirstMappedPort());

        FlagdInProcessSetup.provider = new FlagdProvider(FlagdOptions.builder()
                .resolverType(Config.Resolver.IN_PROCESS)
                // set a generous deadline, to prevent timeouts in actions
                .deadline(3000)
                .targetUri(targetUri)
                .build());
        StepDefinitions.setProvider(provider);
    }

    @AfterAll
    public static void tearDown() {
        flagdContainer.stop();
        envoyContainer.stop();
    }
}