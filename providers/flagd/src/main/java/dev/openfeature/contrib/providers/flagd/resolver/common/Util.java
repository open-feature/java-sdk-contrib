package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.sdk.exceptions.GeneralError;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Utility class for managing gRPC connection states and handling synchronization operations.
 */
@Slf4j
public class Util {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private Util() {
    }

    /**
     * A helper method to block the caller until a condition is met or a timeout occurs.
     *
     * @param deadline          the maximum number of milliseconds to block
     * @param connectedSupplier a function that evaluates to {@code true} when the desired condition is met
     * @throws InterruptedException if the thread is interrupted during the waiting process
     * @throws GeneralError         if the deadline is exceeded before the condition is met
     */
    public static void busyWaitAndCheck(final Long deadline,
                                        final Supplier<Boolean> connectedSupplier) throws InterruptedException {
        long start = System.currentTimeMillis();

        do {
            if (deadline <= System.currentTimeMillis() - start) {
                throw new GeneralError(String.format("Deadline exceeded. Condition did not complete within the %d "
                        + "deadline", deadline));
            }

            Thread.sleep(50L);
        } while (!connectedSupplier.get());
    }

    /**
     * Waits for a gRPC channel to reach a desired state within a specified timeout.
     *
     * @param channel      the gRPC {@link ManagedChannel} to monitor
     * @param desiredState the desired {@link ConnectivityState} to wait for
     * @param callback     a {@link Runnable} to execute when the desired state is reached
     * @param timeout      the maximum time to wait
     * @param unit         the {@link TimeUnit} for the timeout parameter
     * @throws InterruptedException if the waiting thread is interrupted
     * @throws GeneralError         if the deadline is exceeded before reaching the desired state
     */
    public static void waitForDesiredState(ManagedChannel channel, ConnectivityState desiredState, Runnable callback,
                                           long timeout, TimeUnit unit) throws InterruptedException {
        waitForDesiredState(channel, desiredState, callback, new CountDownLatch(1), timeout, unit);
    }

    /**
     * A recursive helper method to monitor a gRPC channel's state until the desired state is reached or timeout occurs.
     *
     * @param channel      the gRPC {@link ManagedChannel} to monitor
     * @param desiredState the desired {@link ConnectivityState} to wait for
     * @param callback     a {@link Runnable} to execute when the desired state is reached
     * @param latch        a {@link CountDownLatch} used for synchronizing the completion of the state change
     * @param timeout      the maximum time to wait
     * @param unit         the {@link TimeUnit} for the timeout parameter
     * @throws InterruptedException if the waiting thread is interrupted
     * @throws GeneralError         if the deadline is exceeded before reaching the desired state
     */
    private static void waitForDesiredState(ManagedChannel channel,
                                            ConnectivityState desiredState,
                                            Runnable callback,
                                            CountDownLatch latch,
                                            long timeout,
                                            TimeUnit unit) throws InterruptedException {
        channel.notifyWhenStateChanged(channel.getState(true), () -> {
            try {
                ConnectivityState state = channel.getState(false);
                log.info("Channel state changed to: {}", state);

                if (state == desiredState) {
                    callback.run();
                    latch.countDown();
                    return;
                }
                waitForDesiredState(channel, desiredState, callback, latch, timeout, unit);
            } catch (Exception e) {
                log.error("Error during state monitoring", e);
            }
        });

        // Await the latch or timeout for the state change
        if (!latch.await(timeout, unit)) {
            throw new GeneralError(String.format("Deadline exceeded. Condition did not complete within the %d "
                    + "deadline", timeout));
        }
    }

    /**
     * Monitors the state of a gRPC {@link ManagedChannel} and triggers callbacks for specific state changes.
     *
     * @param channel           the gRPC {@link ManagedChannel} to monitor
     * @param onConnectionReady a {@link Runnable} to execute when the channel becomes READY
     * @param onConnectionLost  a {@link Runnable} to execute when the channel enters a TRANSIENT_FAILURE state
     */
    public static void monitorChannelState(ManagedChannel channel, Runnable onConnectionReady,
                                           Runnable onConnectionLost) {
        channel.notifyWhenStateChanged(channel.getState(true), () -> {
            ConnectivityState state = channel.getState(false);
            log.debug("Channel state changed to: {}", state);
            if (state == ConnectivityState.READY) {
                onConnectionReady.run();
            }
            if (state == ConnectivityState.TRANSIENT_FAILURE) {
                onConnectionLost.run();
            }
            // Re-register the state monitor to watch for the next state transition.
            monitorChannelState(channel, onConnectionReady, onConnectionLost);
        });
    }
}
