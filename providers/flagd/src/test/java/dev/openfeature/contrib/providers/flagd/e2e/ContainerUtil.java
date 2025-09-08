package dev.openfeature.contrib.providers.flagd.e2e;

import dev.openfeature.contrib.providers.flagd.Config;
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
}
