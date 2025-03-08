package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.ProviderEvent;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.AbstractStub;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * A generic GRPC connector that manages connection states, reconnection logic, and event streaming for
 * GRPC services.
 *
 * @param <T> the type of the asynchronous stub for the GRPC service
 * @param <K> the type of the blocking stub for the GRPC service
 */
@Slf4j
public class ChannelConnector<T extends AbstractStub<T>, K extends AbstractBlockingStub<K>> {

    /**
     * The blocking service stub for making blocking GRPC calls.
     */
    private final Function<ManagedChannel, K> blockingStubFunction;

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
     * A consumer that handles connection events such as connection loss or reconnection.
     */
    private final Consumer<FlagdProviderEvent> onConnectionEvent;

    /**
     * Constructs a new {@code ChannelConnector} instance with the specified options and parameters.
     *
     * @param options             the configuration options for the GRPC connection
     * @param blockingStub        a function to create the blocking service stub from a {@link ManagedChannel}
     * @param onConnectionEvent   a consumer to handle connection events
     * @param channel             the managed channel for the GRPC connection
     */
    public ChannelConnector(
            final FlagdOptions options,
            final Function<ManagedChannel, K> blockingStub,
            final Consumer<FlagdProviderEvent> onConnectionEvent,
            ManagedChannel channel) {

        this.channel = channel;
        this.blockingStubFunction = blockingStub;
        this.deadline = options.getDeadline();
        this.onConnectionEvent = onConnectionEvent;
    }

    /**
     * Constructs a {@code ChannelConnector} instance for testing purposes.
     *
     * @param options             the configuration options for the GRPC connection
     * @param blockingStub        a function to create the blocking service stub from a {@link ManagedChannel}
     * @param onConnectionEvent   a consumer to handle connection events
     */
    public ChannelConnector(
            final FlagdOptions options,
            final Function<ManagedChannel, K> blockingStub,
            final Consumer<FlagdProviderEvent> onConnectionEvent) {
        this(options, blockingStub, onConnectionEvent, ChannelBuilder.nettyChannel(options));
    }

    /**
     * Initializes the GRPC connection by waiting for the channel to be ready and monitoring its state.
     *
     * @throws Exception if the channel does not reach the desired state within the deadline
     */
    public void initialize() throws Exception {
        log.info("Initializing GRPC connection...");
        monitorChannelState(ConnectivityState.READY);
    }

    /**
     * Returns the blocking service stub for making blocking GRPC calls.
     *
     * @return the blocking service stub
     */
    public K getBlockingStub() {
        K stub = blockingStubFunction.apply(channel).withWaitForReady();
        if (this.deadline > 0) {
            stub = stub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS);
        }
        return stub;
    }

    /**
     * Shuts down the GRPC connection and cleans up associated resources.
     *
     * @throws InterruptedException if interrupted while waiting for termination
     */
    public void shutdown() throws InterruptedException {
        log.info("Shutting down GRPC connection...");

        if (!channel.isShutdown()) {
            channel.shutdownNow();
            channel.awaitTermination(deadline, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Monitors the state of a gRPC channel and triggers the specified callbacks based on state changes.
     *
     * @param expectedState     the initial state to monitor.
     */
    private void monitorChannelState(ConnectivityState expectedState) {
        channel.notifyWhenStateChanged(expectedState, this::onStateChange);
    }

    private void onStateChange() {
        ConnectivityState currentState = channel.getState(true);
        log.debug("Channel state changed to: {}", currentState);
        if (currentState == ConnectivityState.TRANSIENT_FAILURE || currentState == ConnectivityState.SHUTDOWN) {
            this.onConnectionEvent.accept(new FlagdProviderEvent(
                    ProviderEvent.PROVIDER_ERROR, Collections.emptyList(), new ImmutableStructure()));
        }
        if (currentState != ConnectivityState.SHUTDOWN) {
            log.debug("continuing to monitor the grpc channel");
            // Re-register the state monitor to watch for the next state transition.
            monitorChannelState(currentState);
        }
    }
}
