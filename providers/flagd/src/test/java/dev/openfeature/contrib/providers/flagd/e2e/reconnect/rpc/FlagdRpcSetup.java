package dev.openfeature.contrib.providers.flagd.e2e.reconnect.rpc;

import dev.openfeature.contrib.providers.flagd.e2e.ContainerConfig;
import io.cucumber.java.AfterAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Isolated;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.reconnect.steps.StepDefinitions;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.CacheType;
import dev.openfeature.sdk.FeatureProvider;
import io.cucumber.java.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@Isolated()
@Order(value = Integer.MAX_VALUE)
public class FlagdRpcSetup {
    private static final GenericContainer flagdContainer = ContainerConfig.flagd(true);

    @BeforeAll()
    public static void setup() throws InterruptedException {
        flagdContainer.start();

        FeatureProvider workingProvider = new FlagdProvider(FlagdOptions.builder()
                .resolverType(Config.Resolver.RPC)
                .port(flagdContainer.getFirstMappedPort())
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
