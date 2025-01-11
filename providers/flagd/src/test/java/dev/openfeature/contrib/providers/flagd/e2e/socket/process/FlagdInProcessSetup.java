package dev.openfeature.contrib.providers.flagd.e2e.socket.process;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.ContainerConfig;
import dev.openfeature.contrib.providers.flagd.e2e.steps.StepDefinitions;
import dev.openfeature.sdk.FeatureProvider;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Isolated;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import org.testcontainers.utility.DockerImageName;


@Isolated()
@Order(value = Integer.MAX_VALUE)
public class FlagdInProcessSetup {

    static Path sharedTempDir;

    static {
        try {
            sharedTempDir = Files.createDirectories(
                    Paths.get("tmp/" + RandomStringUtils.randomAlphanumeric(8).toLowerCase() +"/"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final GenericContainer flagdContainer = new GenericContainer(
            DockerImageName.parse(ContainerConfig.generateContainerName("flagd", "socket")))
            .withFileSystemBind(sharedTempDir.toAbsolutePath().toString(), "/tmp", BindMode.READ_WRITE);

    @BeforeAll()
    public static void setups() throws InterruptedException, IOException {
        flagdContainer.start();
    }

    @AfterAll()
    public static void cleanup() throws IOException {
        flagdContainer.stop();
        FileUtils.deleteDirectory(sharedTempDir.toFile());
    }

    @Before()
    public static void setupTest() throws InterruptedException {
        String string = sharedTempDir.resolve("socket.sock").toAbsolutePath().toString();
        FeatureProvider workingProvider = new FlagdProvider(FlagdOptions.builder()
                .resolverType(Config.Resolver.IN_PROCESS)
                .deadline(10000)
                .streamDeadlineMs(0) // this makes reconnect tests more predictable
                .socketPath(string)
                .build());
        StepDefinitions.setProvider(workingProvider);
    }
}
