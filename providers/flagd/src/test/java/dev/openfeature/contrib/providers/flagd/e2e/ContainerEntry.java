package dev.openfeature.contrib.providers.flagd.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/** A single pre-warmed Docker Compose stack (flagd + envoy) and its associated temp directory. */
public class ContainerEntry {

    public static final int FORBIDDEN_PORT = 9212;

    public final ComposeContainer container;
    public final Path tempDir;

    private ContainerEntry(ComposeContainer container, Path tempDir) {
        this.container = container;
        this.tempDir = tempDir;
    }

    /** Start a new container entry. Blocks until all services are ready. */
    public static ContainerEntry start() throws IOException {
        Path tempDir = Files.createDirectories(
                Paths.get("tmp/" + RandomStringUtils.randomAlphanumeric(8).toLowerCase() + "/"));

        ComposeContainer container = new ComposeContainer(new File("test-harness/docker-compose.yaml"))
                .withEnv("FLAGS_DIR", tempDir.toAbsolutePath().toString())
                .withExposedService("flagd", 8013, Wait.forListeningPort())
                .withExposedService("flagd", 8015, Wait.forListeningPort())
                .withExposedService("flagd", 8080, Wait.forListeningPort())
                .withExposedService("envoy", 9211, Wait.forListeningPort())
                .withExposedService("envoy", FORBIDDEN_PORT, Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(45));
        container.start();

        return new ContainerEntry(container, tempDir);
    }

    /** Stop the container and clean up the temp directory. */
    public void stop() throws IOException {
        container.stop();
        FileUtils.deleteDirectory(tempDir.toFile());
    }
}
