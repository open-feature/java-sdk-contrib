package dev.openfeature.contrib.providers.flagd.e2e;

import com.github.dockerjava.api.command.PauseContainerCmd;
import com.github.dockerjava.api.command.SyncDockerCmd;
import com.github.dockerjava.api.command.UnpauseContainerCmd;
import dev.openfeature.contrib.providers.flagd.Config;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class FlagdContainer extends GenericContainer<FlagdContainer> {
    private static final String version;
    private static final Network network = Network.newNetwork();

    static {
        String path = "test-harness/version.txt";
        File file = new File(path);
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            version = lines.get(0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String feature;

    public FlagdContainer() {
        this("");
    }

    public FlagdContainer(String feature) {
        super(generateContainerName(feature));
        this.withReuse(true);
        this.feature = feature;
        if (!"socket".equals(this.feature))
            this.addExposedPorts(8013, 8014, 8015, 8016);
    }


    public int getPort(Config.Resolver resolver) {
        waitUntilContainerStarted();
        switch (resolver) {
            case RPC:
                return this.getMappedPort(8013);
            case IN_PROCESS:
                return this.getMappedPort(8015);
            default:
                throw new IllegalArgumentException("Unsupported resolver: " + resolver);
        }
    }


    /**
     * @return a {@link org.testcontainers.containers.GenericContainer} instance of envoy container using
     * flagd sync service as backend expose on port 9211
     */
    public static GenericContainer envoy() {
        final String container = "envoyproxy/envoy:v1.31.0";
        return new GenericContainer(DockerImageName.parse(container))
                .withCopyFileToContainer(MountableFile.forClasspathResource("/envoy-config/envoy-custom.yaml"),
                        "/etc/envoy/envoy.yaml")
                .withExposedPorts(9211)
                .withNetwork(network)
                .withNetworkAliases("envoy");
    }

    public static @NotNull String generateContainerName(String feature) {
        String container = "ghcr.io/open-feature/flagd-testbed";
        if (!Strings.isBlank(feature)) {
            container += "-" + feature;
        }
        container += ":v" + version;
        return container;
    }
}
