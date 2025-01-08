package dev.openfeature.contrib.providers.flagd.e2e.reconnect.rpc;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.ContainerConfig;
import dev.openfeature.contrib.providers.flagd.e2e.reconnect.steps.StepDefinitions;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.CacheType;
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
    private static final GenericContainer flagdContainer = ContainerConfig.flagd(true);

    @BeforeAll()
    public static void setups() throws InterruptedException {
        flagdContainer.start();
    }

    @Before()
    public static void setupTest() throws InterruptedException {

        FeatureProvider workingProvider = new FlagdProvider(FlagdOptions.builder()
                .resolverType(Config.Resolver.RPC)
                .port(flagdContainer.getFirstMappedPort())
                .deadline(1000)
                .streamRetryGracePeriod(1)
                .streamDeadlineMs(0) // this makes reconnect tests more predictable
                .cacheType(CacheType.DISABLED.getValue())
                .build());
        StepDefinitions.setUnstableProvider(workingProvider);

        FeatureProvider unavailableProvider = new FlagdProvider(FlagdOptions.builder()
                .resolverType(Config.Resolver.RPC)
                .port(8015) // this port isn't serving anything, error expected
                .deadline(100)
                .cacheType(CacheType.DISABLED.getValue())
                .build());
        StepDefinitions.setUnavailableProvider(unavailableProvider);
    }

    @AfterAll
    public static void tearDown() {
        flagdContainer.stop();
    }
}
