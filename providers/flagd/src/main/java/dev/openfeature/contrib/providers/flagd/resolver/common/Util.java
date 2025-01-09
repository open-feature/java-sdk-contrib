package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for managing gRPC connection states and handling synchronization operations.
 */
@Slf4j
public class Util {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private Util() {}

    /**
     * A helper method to block the caller until a condition is met or a timeout occurs.
     *
     * @param deadline          the maximum number of milliseconds to block
     * @param connectedSupplier a function that evaluates to {@code true} when the desired condition is met
     * @throws InterruptedException if the thread is interrupted during the waiting process
     * @throws GeneralError         if the deadline is exceeded before the condition is met
     */
    public static void busyWaitAndCheck(final Long deadline, final Supplier<Boolean> connectedSupplier)
            throws InterruptedException {
        long start = System.currentTimeMillis();

        do {
            if (deadline <= System.currentTimeMillis() - start) {
                throw new GeneralError(String.format(
                        "Deadline exceeded. Condition did not complete within the %d " + "deadline", deadline));
            }

            Thread.sleep(50L);
        } while (!connectedSupplier.get());
    }
}
