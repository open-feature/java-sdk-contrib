package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.exceptions.GeneralError;
import lombok.Getter;
import lombok.Setter;

/**
 * Contains all fields we need to worry about locking, used as intrinsic lock
 * for sync blocks in the {@link FlagdProvider}.
 */
@Getter
class FlagdProviderSyncResources {
    @Setter
    private volatile ProviderEvent previousEvent = null;

    private volatile EvaluationContext enrichedContext = new ImmutableContext();
    private volatile boolean initialized;
    private volatile boolean isShutDown;

    public void setEnrichedContext(EvaluationContext context) {
        this.enrichedContext = new ImmutableContext(context.asMap());
    }

    /**
     * With this method called, it is suggested that initialization has been completed. It will wake up all threads that
     * wait for the initialization. Subsequent calls have no effect.
     *
     * @return true iff this was the first call to {@code initialize()}
     */
    public synchronized boolean initialize() {
        if (this.initialized || this.isShutDown) {
            return false;
        }
        this.initialized = true;
        this.notifyAll();
        return true;
    }

    /**
     * Blocks the calling thread until either {@link FlagdProviderSyncResources#initialize()} or
     * {@link FlagdProviderSyncResources#shutdown()} is called or the deadline is exceeded, whatever happens first. If
     * {@link FlagdProviderSyncResources#initialize()} has been executed before {@code waitForInitialization(long)} is
     * called, it will return instantly. If the deadline is exceeded, a GeneralError will be thrown.
     * If {@link FlagdProviderSyncResources#shutdown()} is called in the meantime, an {@link IllegalStateException} will
     * be thrown. Otherwise, the method will return cleanly.
     *
     * @param deadline the maximum time in ms to wait
     * @throws GeneralError          when the deadline is exceeded before
     *                               {@link FlagdProviderSyncResources#initialize()} is called on this object
     * @throws IllegalStateException when {@link FlagdProviderSyncResources#shutdown()} is called or has been called on
     *                               this object
     */
    public void waitForInitialization(long deadline) {
        long start = System.currentTimeMillis();
        long end = start + deadline;
        while (!initialized && !isShutDown) {
            long now = System.currentTimeMillis();
            // if wait(0) is called, the thread would wait forever, so we abort when this would happen
            if (now >= end) {
                throw new GeneralError(String.format(
                        "Initialization timeout exceeded; did not complete within the %d ms deadline.", deadline));
            }
            long remaining = end - now;
            synchronized (this) {
                if (isShutDown) {
                    break;
                }
                if (initialized) { // might have changed in the meantime
                    return;
                }
                try {
                    this.wait(remaining);
                } catch (InterruptedException e) {
                    // try again. Leave the continue to make PMD happy
                    continue;
                }
            }
        }
        if (isShutDown) {
            throw new IllegalStateException("Already shut down");
        }
    }

    /**
     * Signals a shutdown. Threads waiting for initialization will wake up and throw an {@link IllegalStateException}.
     */
    public synchronized void shutdown() {
        isShutDown = true;
        this.notifyAll();
    }
}
