package dev.openfeature.contrib.providers.flagd.resolver.grpc;

import com.google.common.annotations.VisibleForTesting;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelBuilder;
import dev.openfeature.contrib.providers.flagd.resolver.common.ConnectionEvent;
import dev.openfeature.contrib.providers.flagd.resolver.common.ConnectionState;
import dev.openfeature.contrib.providers.flagd.resolver.common.Util;
import dev.openfeature.sdk.ImmutableStructure;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.AbstractStub;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

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
    private final Consumer<ConnectionEvent> onConnectionEvent;

    /**
     * A consumer that handles GRPC service stubs for event stream handling.
     */
    private final Consumer<T> streamObserver;

    /**
     * An executor service responsible for scheduling reconnection attempts.
     */
    private final ScheduledExecutorService reconnectExecutor;

    /**
     * The grace period in milliseconds to wait for reconnection before emitting an error event.
     */
    private final long gracePeriod;

    /**
     * Indicates whether the connector is currently connected to the GRPC service.
     */
    @Getter
    private boolean connected = false;

    /**
     * A scheduled task for managing reconnection attempts.
     */
    private ScheduledFuture<?> reconnectTask;

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
    public GrpcConnector(final FlagdOptions options,
                         final Function<ManagedChannel, T> stub,
                         final Function<ManagedChannel, K> blockingStub,
                         final Consumer<ConnectionEvent> onConnectionEvent,
                         final Consumer<T> eventStreamObserver, ManagedChannel channel) {

        this.channel = channel;
        this.serviceStub = stub.apply(channel);
        this.blockingStub = blockingStub.apply(channel);
        this.deadline = options.getDeadline();
        this.streamDeadlineMs = options.getStreamDeadlineMs();
        this.onConnectionEvent = onConnectionEvent;
        this.streamObserver = eventStreamObserver;
        this.gracePeriod = options.getStreamRetryGracePeriod();
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
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
    @VisibleForTesting
    GrpcConnector(final FlagdOptions options,
                  final Function<ManagedChannel, T> stub,
                  final Function<ManagedChannel, K> blockingStub,
                  final Consumer<ConnectionEvent> onConnectionEvent,
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
        Util.waitForDesiredState(channel, ConnectivityState.READY, this::onReady, deadline, TimeUnit.MILLISECONDS);
        Util.monitorChannelState(channel, this::onReady, this::onConnectionLost);
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
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdownNow();
            reconnectExecutor.awaitTermination(deadline, TimeUnit.MILLISECONDS);
        }

        if (!channel.isShutdown()) {
            channel.shutdownNow();
            channel.awaitTermination(deadline, TimeUnit.MILLISECONDS);
        }

        this.onConnectionEvent.accept(new ConnectionEvent(false));
    }

    /**
     * Handles the event when the GRPC channel becomes ready, marking the connection as established.
     * Cancels any pending reconnection task and restarts the event stream.
     */
    private synchronized void onReady() {
        connected = true;

        if (reconnectTask != null && !reconnectTask.isCancelled()) {
            reconnectTask.cancel(false);
            log.debug("Reconnection task cancelled as connection became READY.");
        }
        restartStream();
    }

    /**
     * Handles the event when the GRPC channel loses its connection, marking the connection as lost.
     * Schedules a reconnection task after a grace period and emits a stale connection event.
     */
    private synchronized void onConnectionLost() {
        log.debug("Connection lost. Emit STALE event...");
        log.debug("Waiting {}ms for connection to become available...", gracePeriod);
        connected = false;

        this.onConnectionEvent.accept(
                new ConnectionEvent(
                        ConnectionState.STALE,
                        Collections.emptyList(),
                        new ImmutableStructure()));

        if (reconnectTask != null && !reconnectTask.isCancelled()) {
            reconnectTask.cancel(false);
        }
        reconnectTask = reconnectExecutor.schedule(() -> {
            log.debug("Provider did not reconnect successfully within {}ms. Emit ERROR event...", gracePeriod);
            this.onConnectionEvent.accept(
                    new ConnectionEvent(false));
        }, gracePeriod, TimeUnit.MILLISECONDS);
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
            this.onConnectionEvent.accept(new ConnectionEvent(true));
            return;
        }
        log.debug("Stream restart skipped. Not connected.");
    }
}