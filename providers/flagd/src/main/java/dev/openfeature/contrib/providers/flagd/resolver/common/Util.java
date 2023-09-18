package dev.openfeature.contrib.providers.flagd.resolver.common;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utils for flagd resolvers.
 */
public class Util {
    /**
     * A helper to block the caller for given conditions.
     *
     * @param deadline number of milliseconds to block
     * @param check    {@link AtomicBoolean} to check for status true
     */
    public static void busyWaitAndCheck(final Long deadline, final AtomicBoolean check) throws InterruptedException {
        long start = System.currentTimeMillis();

        do {
            if (deadline <= System.currentTimeMillis() - start) {
                throw new RuntimeException(
                    String.format("Deadline exceeded. Condition did not complete within the %d deadline", deadline));
            }

            Thread.sleep(50L);
        } while (!check.get());
    }
}
