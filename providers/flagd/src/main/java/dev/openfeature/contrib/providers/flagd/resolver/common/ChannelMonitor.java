package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.sdk.exceptions.GeneralError;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


/**
 * A utility class to monitor and manage the connectivity state of a gRPC ManagedChannel.
 */
@Slf4j
public class ChannelMonitor {


    private ChannelMonitor() {

    }

    /**
     * Monitors the state of a gRPC channel and triggers the specified callbacks based on state changes.
     *
     * @param expectedState     the initial state to monitor.
     * @param channel           the ManagedChannel to monitor.
     * @param onConnectionReady callback invoked when the channel transitions to a READY state.
     * @param onConnectionLost  callback invoked when the channel transitions to a FAILURE or SHUTDOWN state.
     */
    public static void monitorChannelState(ConnectivityState expectedState, ManagedChannel channel,
                                           Runnable onConnectionReady, Runnable onConnectionLost) {
        channel.notifyWhenStateChanged(expectedState, () -> {
            ConnectivityState currentState = channel.getState(true);
            log.info("Channel state changed to: {}", currentState);
            if (currentState == ConnectivityState.READY) {
                onConnectionReady.run();
            } else if (currentState == ConnectivityState.TRANSIENT_FAILURE
                    || currentState == ConnectivityState.SHUTDOWN) {
                onConnectionLost.run();
            }
            // Re-register the state monitor to watch for the next state transition.
            monitorChannelState(currentState, channel, onConnectionReady, onConnectionLost);
        });
    }


    /**
     * Waits for the channel to reach a desired state within a specified timeout period.
     *
     * @param channel         the ManagedChannel to monitor.
     * @param desiredState    the ConnectivityState to wait for.
     * @param connectCallback callback invoked when the desired state is reached.
     * @param timeout         the maximum amount of time to wait.
     * @param unit            the time unit of the timeout.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     */
    public static void waitForDesiredState(ManagedChannel channel,
                                           ConnectivityState desiredState,
                                           Runnable connectCallback,
                                           long timeout,
                                           TimeUnit unit) throws InterruptedException {
        waitForDesiredState(channel, desiredState, connectCallback, new CountDownLatch(1), timeout, unit);
    }


    private static void waitForDesiredState(ManagedChannel channel,
                                            ConnectivityState desiredState,
                                            Runnable connectCallback,
                                            CountDownLatch latch,
                                            long timeout,
                                            TimeUnit unit) throws InterruptedException {
        channel.notifyWhenStateChanged(ConnectivityState.SHUTDOWN, () -> {
            try {
                ConnectivityState state = channel.getState(true);
                log.debug("Channel state changed to: {}", state);

                if (state == desiredState) {
                    connectCallback.run();
                    latch.countDown();
                    return;
                }
                waitForDesiredState(channel, desiredState, connectCallback, latch, timeout, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread interrupted while waiting for desired state", e);
            } catch (Exception e) {
                log.error("Error occurred while waiting for desired state", e);
            }
        });

        // Await the latch or timeout for the state change
        if (!latch.await(timeout, unit)) {
            throw new GeneralError(String.format("Deadline exceeded. Condition did not complete within the %d "
                    + "deadline", timeout));
        }
    }


    /**
     * Polls the state of a gRPC channel at regular intervals and triggers callbacks upon state changes.
     *
     * @param executor          the ScheduledExecutorService used for polling.
     * @param channel           the ManagedChannel to monitor.
     * @param onConnectionReady callback invoked when the channel transitions to a READY state.
     * @param onConnectionLost  callback invoked when the channel transitions to a FAILURE or SHUTDOWN state.
     * @param pollIntervalMs    the polling interval in milliseconds.
     */
    public static void pollChannelState(ScheduledExecutorService executor, ManagedChannel channel,
                                        Runnable onConnectionReady,
                                        Runnable onConnectionLost, long pollIntervalMs) {

        AtomicReference<ConnectivityState> lastState = new AtomicReference<>(ConnectivityState.READY);

        Runnable pollTask = () -> {
            ConnectivityState currentState = channel.getState(true);
            if (currentState != lastState.get()) {
                if (currentState == ConnectivityState.READY) {
                    log.debug("gRPC connection became READY");
                    onConnectionReady.run();
                } else if (currentState == ConnectivityState.TRANSIENT_FAILURE
                        || currentState == ConnectivityState.SHUTDOWN) {
                    log.debug("gRPC connection became TRANSIENT_FAILURE");
                    onConnectionLost.run();
                }
                lastState.set(currentState);
            }
        };
        executor.scheduleAtFixedRate(pollTask, 0, pollIntervalMs, TimeUnit.MILLISECONDS);
    }


    /**
     * Polls the channel state at fixed intervals and waits for the channel to reach a desired state within a timeout
     * period.
     *
     * @param executor        the ScheduledExecutorService used for polling.
     * @param channel         the ManagedChannel to monitor.
     * @param desiredState    the ConnectivityState to wait for.
     * @param connectCallback callback invoked when the desired state is reached.
     * @param timeout         the maximum amount of time to wait.
     * @param unit            the time unit of the timeout.
     * @return {@code true} if the desired state was reached within the timeout period, {@code false} otherwise.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     */
    public static boolean pollForDesiredState(ScheduledExecutorService executor, ManagedChannel channel,
                                              ConnectivityState desiredState, Runnable connectCallback, long timeout,
                                              TimeUnit unit) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Runnable waitForStateTask = () -> {
            ConnectivityState currentState = channel.getState(true);
            if (currentState == desiredState) {
                connectCallback.run();
                latch.countDown();
            }
        };

        ScheduledFuture<?> scheduledFuture = executor.scheduleWithFixedDelay(waitForStateTask, 0, 100,
                TimeUnit.MILLISECONDS);

        boolean success = latch.await(timeout, unit);
        scheduledFuture.cancel(true);
        return success;
    }
}
