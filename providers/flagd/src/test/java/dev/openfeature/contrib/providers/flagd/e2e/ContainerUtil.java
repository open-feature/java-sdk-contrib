package dev.openfeature.contrib.providers.flagd.e2e;

import dev.openfeature.contrib.providers.flagd.Config;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;

public class ContainerUtil {
    public static int getPort(ComposeContainer container, Config.Resolver resolver) {
        Optional<ContainerState> flagd = container.getContainerByServiceName("flagd");

        return flagd.map(containerState -> {
                    switch (resolver) {
                        case RPC:
                            return containerState.getMappedPort(8013);
                        case IN_PROCESS:
                            return containerState.getMappedPort(8015);
                        default:
                            return 0;
                    }
                })
                .orElseThrow(() -> new RuntimeException("Could not map port"));
    }

    public static String getLaunchpadUrl(ComposeContainer container) {
        Optional<ContainerState> flagd = container.getContainerByServiceName("flagd");
        return flagd.map(containerState -> {
                    return containerState.getHost() + ":" + containerState.getMappedPort(8080);
                })
                .orElseThrow(() -> new RuntimeException("Could not find launchpad url"));
    }

    /**
     * Blocks until the given flagd service port accepts TCP connections, or the timeout elapses.
     * The launchpad's {@code /start} endpoint polls flagd's HTTP {@code /readyz} before returning,
     * but the gRPC ports (8013, 8015) may become available slightly later. Waiting here prevents
     * {@code setProviderAndWait} from timing out under parallel load.
     */
    public static void waitForGrpcPort(ComposeContainer container, Config.Resolver resolver, long timeoutMs)
            throws InterruptedException {
        int internalPort;
        switch (resolver) {
            case RPC:
                internalPort = 8013;
                break;
            case IN_PROCESS:
                internalPort = 8015;
                break;
            default:
                return;
        }
        ContainerState state = container
                .getContainerByServiceName("flagd")
                .orElseThrow(() -> new RuntimeException("Could not find flagd container"));
        String host = state.getHost();
        int mappedPort = state.getMappedPort(internalPort);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, mappedPort), 100);
                return;
            } catch (IOException ignored) {
                Thread.sleep(50);
            }
        }
    }
}
