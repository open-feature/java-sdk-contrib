package dev.openfeature.contrib.providers.flagd.e2e.ssl.process;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.ContainerConfig;
import dev.openfeature.contrib.providers.flagd.e2e.steps.StepDefinitions;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.CacheType;
import dev.openfeature.sdk.FeatureProvider;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Isolated;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;

@Isolated()
@Order(value = Integer.MAX_VALUE)
public class FlagdInProcessSetup {
    private static final GenericContainer flagdContainer =
            new GenericContainer(
                    DockerImageName.parse(
                            ContainerConfig.generateContainerName("flagd", "ssl")
                    )
            ).withExposedPorts(8015);

    @BeforeAll()
    public static void setups() throws InterruptedException {
        flagdContainer.start();
    }

    @Before()
    public static void setupTest() throws InterruptedException {
        String path = "test-harness/ssl/custom-root-cert.crt";

        File file = new File(path);
        String absolutePath = file.getAbsolutePath();
        FeatureProvider workingProvider = new FlagdProvider(FlagdOptions.builder()
                .resolverType(Config.Resolver.IN_PROCESS)
                .port(flagdContainer.getFirstMappedPort())
                .deadline(10000)
                .streamDeadlineMs(0) // this makes reconnect tests more predictable
                .tls(true)
                .certPath(absolutePath)
                .build());
        StepDefinitions.setProvider(workingProvider);

    }

    @AfterAll
    public static void tearDown() {
        flagdContainer.stop();
    }
}
