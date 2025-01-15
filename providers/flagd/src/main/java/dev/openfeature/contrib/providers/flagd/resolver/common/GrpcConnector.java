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
public class GrpcConnector<T extends AbstractStub<T>, K extends AbstractBlockingStub<K>> {

    /**
     * The asynchronous service stub for making non-blocking GRPC calls.
     */
    private final T serviceStub;

    /**
     * The blocking service stub for making blocking GRPC calls.
     */
    private final K blockingStub;

    /**
     * The GRPC managed channel for managing the underlying GRPC connection.
     */
    private final ManagedChannel channel;

    /**
     * The deadline in milliseconds for GRPC operations.
     */
    private final long deadline;

    /**
     * The deadline in milliseconds for event streaming operations.
     */
    private final long streamDeadlineMs;

    /**
     * A consumer that handles connection events such as connection loss or reconnection.
     */
    private final Consumer<FlagdProviderEvent> onConnectionEvent;

    /**
     * A consumer that handles GRPC service stubs for event stream handling.
     */
    private final Consumer<T> streamObserver;

    /**
     * Indicates whether the connector is currently connected to the GRPC service.
     */
    @Getter
    private boolean connected = false;

    /**
     * Constructs a new {@code GrpcConnector} instance with the specified options and parameters.
     *
     * @param options             the configuration options for the GRPC connection
     * @param stub                a function to create the asynchronous service stub from a {@link ManagedChannel}
     * @param blockingStub        a function to create the blocking service stub from a {@link ManagedChannel}
     * @param onConnectionEvent   a consumer to handle connection events
     * @param eventStreamObserver a consumer to handle the event stream
     * @param channel             the managed channel for the GRPC connection
     */
    public GrpcConnector(
            final FlagdOptions options,
            final Function<ManagedChannel, T> stub,
            final Function<ManagedChannel, K> blockingStub,
            final Consumer<FlagdProviderEvent> onConnectionEvent,
            final Consumer<T> eventStreamObserver,
            ManagedChannel channel) {

        this.channel = channel;
        this.serviceStub = stub.apply(channel).withWaitForReady();
        this.blockingStub = blockingStub.apply(channel).withWaitForReady();
        this.deadline = options.getDeadline();
        this.streamDeadlineMs = options.getStreamDeadlineMs();
        this.onConnectionEvent = onConnectionEvent;
        this.streamObserver = eventStreamObserver;
    }

    /**
     * Constructs a {@code GrpcConnector} instance for testing purposes.
     *
     * @param options             the configuration options for the GRPC connection
     * @param stub                a function to create the asynchronous service stub from a {@link ManagedChannel}
     * @param blockingStub        a function to create the blocking service stub from a {@link ManagedChannel}
     * @param onConnectionEvent   a consumer to handle connection events
     * @param eventStreamObserver a consumer to handle the event stream
     */
    public GrpcConnector(
            final FlagdOptions options,
            final Function<ManagedChannel, T> stub,
            final Function<ManagedChannel, K> blockingStub,
            final Consumer<FlagdProviderEvent> onConnectionEvent,
            final Consumer<T> eventStreamObserver) {
        this(options, stub, blockingStub, onConnectionEvent, eventStreamObserver, ChannelBuilder.nettyChannel(options));
    }

    /**
     * Initializes the GRPC connection by waiting for the channel to be ready and monitoring its state.
     *
     * @throws Exception if the channel does not reach the desired state within the deadline
     */
    public void initialize() throws Exception {
        log.info("Initializing GRPC connection...");
        ChannelMonitor.monitorChannelState(ConnectivityState.READY, channel, this::onReady, this::onConnectionLost);
    }

    /**
     * Returns the blocking service stub for making blocking GRPC calls.
     *
     * @return the blocking service stub
     */
    public K getResolver() {
        return blockingStub;
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

    private synchronized void onInitialConnect() {
        connected = true;
        restartStream();
    }

    /**
     * Handles the event when the GRPC channel becomes ready, marking the connection as established.
     * Cancels any pending reconnection task and restarts the event stream.
     */
    private synchronized void onReady() {
        connected = true;
        restartStream();
    }

    /**
     * Handles the event when the GRPC channel loses its connection, marking the connection as lost.
     * Schedules a reconnection task after a grace period and emits a stale connection event.
     */
    private synchronized void onConnectionLost() {
        connected = false;

        this.onConnectionEvent.accept(new FlagdProviderEvent(
                ProviderEvent.PROVIDER_ERROR, Collections.emptyList(), new ImmutableStructure()));
    }

    /**
     * Restarts the event stream using the asynchronous service stub, applying a deadline if configured.
     * Emits a connection event if the restart is successful.
     */
    private synchronized void restartStream() {
        if (connected) {
            log.debug("(Re)initializing event stream.");
            T localServiceStub = this.serviceStub;
            if (streamDeadlineMs > 0) {
                localServiceStub = localServiceStub.withDeadlineAfter(this.streamDeadlineMs, TimeUnit.MILLISECONDS);
            }
            streamObserver.accept(localServiceStub);
            return;
        }
        log.debug("Stream restart skipped. Not connected.");
    }
}
