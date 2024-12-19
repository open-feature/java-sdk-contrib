package dev.openfeature.contrib.providers.flagd.e2e;

import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.util.Properties;

public class ContainerConfig {
    private static final String version;
    private static final Network network = Network.newNetwork();

    static {
        Properties properties = new Properties();
        try {
            properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("flagdTestbed.properties"));
            version = properties.getProperty("version");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *
     * @return a {@link GenericContainer} instance of a stable sync flagd server with the port 9090 exposed
     */
    public static GenericContainer sync()  {
        return sync(false, false);
    }

    /**
     *
     * @param unstable if an unstable version of the container, which terminates the connection regularly should be used.
     * @param addNetwork if set to true a custom network is attached for cross container access e.g. envoy --> sync:8015
     * @return a {@link GenericContainer} instance of a sync flagd server with the port 8015 exposed
     */
    public static GenericContainer sync(boolean unstable, boolean addNetwork) {
        String container = generateContainerName("flagd", unstable);
        GenericContainer genericContainer = new GenericContainer(DockerImageName.parse(container))
                .withExposedPorts(8015);

        if (addNetwork) {
            genericContainer.withNetwork(network);
            genericContainer.withNetworkAliases("sync-service");
        }

        return genericContainer;
    }

    /**
     *
     * @return a {@link GenericContainer} instance of a stable flagd server with the port 8013 exposed
     */
    public static GenericContainer flagd() {
        return flagd(false);
    }

    /**
     *
     * @param unstable if an unstable version of the container, which terminates the connection regularly should be used.
     * @return a {@link GenericContainer} instance of a flagd server with the port 8013 exposed
     */
    public static GenericContainer flagd(boolean unstable) {
        String container = generateContainerName("flagd", unstable);
        return new GenericContainer(DockerImageName.parse(container))
                .withExposedPorts(8013);
    }


    /**
     * @return a {@link GenericContainer} instance of envoy container using
     * flagd sync service as backend expose on port 9211
     *
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

    private static @NotNull String generateContainerName(String type, boolean unstable) {
        String container = "ghcr.io/open-feature/";
        container += type;
        container += "-testbed";
        if (unstable) {
            container += "-unstable";
        }
        container += ":" + version;
        return container;
    }
}
