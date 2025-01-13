package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.sdk.exceptions.GeneralError;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * A utility class to monitor and manage the connectivity state of a gRPC ManagedChannel.
 */
@Slf4j
public class ChannelMonitor {

    private ChannelMonitor() {}

    /**
     * Monitors the state of a gRPC channel and triggers the specified callbacks based on state changes.
     *
     * @param expectedState     the initial state to monitor.
     * @param channel           the ManagedChannel to monitor.
     * @param onConnectionReady callback invoked when the channel transitions to a READY state.
     * @param onConnectionLost  callback invoked when the channel transitions to a FAILURE or SHUTDOWN state.
     */
    public static void monitorChannelState(
            ConnectivityState expectedState,
            ManagedChannel channel,
            Runnable onConnectionReady,
            Runnable onConnectionLost) {
        channel.notifyWhenStateChanged(expectedState, () -> {
            ConnectivityState currentState = channel.getState(true);
            log.info("Channel state changed to: {}", currentState);
            if (currentState == ConnectivityState.READY) {
                if (onConnectionReady != null) {
                    onConnectionReady.run();
                } else {
                    log.debug("onConnectionReady is null");
                }
            } else if (currentState == ConnectivityState.TRANSIENT_FAILURE
                    || currentState == ConnectivityState.SHUTDOWN) {
                if (onConnectionLost != null) {
                    onConnectionLost.run();
                } else {
                    log.debug("onConnectionLost is null");
                }
            }
            // Re-register the state monitor to watch for the next state transition.
            monitorChannelState(currentState, channel, onConnectionReady, onConnectionLost);
        });
    }

    /**
     * Waits for the channel to reach the desired connectivity state within the specified timeout.
     *
     * @param desiredState    the desired {@link ConnectivityState} to wait for
     * @param channel         the {@link ManagedChannel} to monitor
     * @param connectCallback the {@link Runnable} to execute when the desired state is reached
     * @param timeout         the maximum time to wait
     * @param unit            the time unit of the timeout argument
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws GeneralError         if the desired state is not reached within the timeout
     */
    public static void waitForDesiredState(
            ConnectivityState desiredState,
            ManagedChannel channel,
            Runnable connectCallback,
            long timeout,
            TimeUnit unit)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Runnable waitForStateTask = () -> {
            ConnectivityState currentState = channel.getState(true);
            if (currentState == desiredState) {
                connectCallback.run();
                latch.countDown();
            }
        };

        ScheduledFuture<?> scheduledFuture = Executors.newSingleThreadScheduledExecutor()
                .scheduleWithFixedDelay(waitForStateTask, 0, 100, TimeUnit.MILLISECONDS);

        boolean success = latch.await(timeout, unit);
        scheduledFuture.cancel(true);
        if (!success) {
            throw new GeneralError(String.format(
                    "Deadline exceeded. Condition did not complete within the %d " + "deadline", timeout));
        }
    }
}
