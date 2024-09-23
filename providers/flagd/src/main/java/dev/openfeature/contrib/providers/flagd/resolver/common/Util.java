package dev.openfeature.contrib.providers.flagd.resolver.common;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import dev.openfeature.sdk.exceptions.GeneralError;

/**
 * Utils for flagd resolvers.
 */
public class Util {

    private Util() {
    }

    /**
     * A helper to block the caller for given conditions.
     * @param deadline number of milliseconds to block
     * @param connectedSupplier func to check for status true
     * @throws InterruptedException
     */
    public static void busyWaitAndCheck(final Long deadline, final Supplier<Boolean> connectedSupplier) throws InterruptedException {
        long start = System.currentTimeMillis();

        do {
            if (deadline <= System.currentTimeMillis() - start) {
                throw new GeneralError(
                    String.format("Deadline exceeded. Condition did not complete within the %d deadline", deadline));
            }

            Thread.sleep(50L);
        } while (!connectedSupplier.get());
    }
}
