package dev.openfeature.contrib.providers.flagd.e2e;

import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Properties;

public class ContainerConfig {
    private static final String version;

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
     * @return a {@link org.testcontainers.containers.GenericContainer} instance of a stable sync flagd server with the port 9090 exposed
     */
    public static GenericContainer sync() {
        return sync(false);
    }

    /**
     *
     * @param unstable if an unstable version of the container, which terminates the connection regularly should be used.
     * @return a {@link org.testcontainers.containers.GenericContainer} instance of a sync flagd server with the port 9090 exposed
     */
    public static GenericContainer sync(boolean unstable) {
        String container = generateContainerName("sync", unstable);
        return new GenericContainer(DockerImageName.parse(container))
                .withExposedPorts(9090);
    }

    /**
     *
     * @return a {@link org.testcontainers.containers.GenericContainer} instance of a stable flagd server with the port 8013 exposed
     */
    public static GenericContainer flagd() {
        return flagd(false);
    }

    /**
     *
     * @param unstable if an unstable version of the container, which terminates the connection regularly should be used.
     * @return a {@link org.testcontainers.containers.GenericContainer} instance of a flagd server with the port 8013 exposed
     */
    public static GenericContainer flagd(boolean unstable) {
        String container = generateContainerName("flagd", unstable);
        return new GenericContainer(DockerImageName.parse(container))
                .withExposedPorts(8013);
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
