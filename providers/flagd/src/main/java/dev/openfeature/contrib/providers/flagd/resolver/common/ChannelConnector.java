package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import io.grpc.ManagedChannel;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * A GRPC connector that maintains a managed channel for communication with a flagd server and handles shutdown.
 */
@Slf4j
public class ChannelConnector {

    /**
     * The GRPC managed channel for managing the underlying GRPC connection.
     */
    @Getter
    private final ManagedChannel channel;

    /**
     * The deadline in milliseconds for GRPC operations.
     */
    private final long deadline;

    /**
     * Constructs a new {@code ChannelConnector} instance with the specified options and parameters.
     *
     * @param options             the configuration options for the GRPC connection
     * @param channel             the managed channel for the GRPC connection
     */
    public ChannelConnector(final FlagdOptions options, ManagedChannel channel) {
        this.channel = channel;
        this.deadline = options.getDeadline();
    }

    /**
     * Shuts down the GRPC connection and cleans up associated resources.
     *
     * @throws InterruptedException if interrupted while waiting for termination
     */
    public void shutdown() throws InterruptedException {
        log.info("Shutting down GRPC connection.");

        if (!channel.isShutdown()) {
            channel.shutdownNow();
            channel.awaitTermination(deadline, TimeUnit.MILLISECONDS);
        }
    }
}
