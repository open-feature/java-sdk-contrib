package dev.openfeature.contrib.providers.flagd.resolver.common;

/**
 * Utility for shutdown operations that involve interruptible waits.
 */
public final class ShutdownUtils {

    private ShutdownUtils() {}

    /** An action that may throw InterruptedException. */
    @FunctionalInterface
    public interface InterruptibleAction {
        void run() throws InterruptedException;
    }

    /**
     * Run an action that may throw InterruptedException (e.g. awaitTermination),
     * suppressing the exception and restoring the interrupt flag.
     */
    public static void awaitTerminationQuietly(InterruptibleAction action) {
        try {
            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
