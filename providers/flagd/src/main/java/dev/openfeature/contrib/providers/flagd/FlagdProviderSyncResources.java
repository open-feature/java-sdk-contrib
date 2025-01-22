package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.exceptions.GeneralError;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Contains all fields we need to worry about locking, used as intrinsic lock
 * for sync blocks in the {@link FlagdProvider}.
 */
@Slf4j
@Getter
class FlagdProviderSyncResources {
    @Setter
    private volatile ProviderEvent previousEvent = null;

    private volatile ImmutableStructure syncMetadata = new ImmutableStructure();
    private volatile EvaluationContext enrichedContext = new ImmutableContext();
    private volatile boolean initialized;
    private volatile boolean isShutDown;

    public void setSyncMetadata(Structure syncMetadata) {
        this.syncMetadata = new ImmutableStructure(syncMetadata.asMap());
    }

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
        log.info("initialize in wait");
        if (this.initialized) {
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
        log.info("wait for init");
        long start = System.currentTimeMillis();
        long end = start + deadline;
        while (!initialized && !isShutDown) {
            long now = System.currentTimeMillis();
            // if wait(0) is called, the thread would wait forever, so we abort when this would happen
            if (now >= end) {
                throw new GeneralError(String.format(
                        "Deadline exceeded. Condition did not complete within the %d ms deadline", deadline));
            }
            long remaining = end - now;
            synchronized (this) {
                if (initialized) { // might have changed in the meantime
                    return;
                }
                if (isShutDown) {
                    break;
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
        log.info("post wait for init");
    }

    /**
     * Signals a shutdown. Threads waiting for initialization will wake up and throw an {@link IllegalStateException}.
     */
    public synchronized void shutdown() {
        log.info("shutdown in wait");
        isShutDown = true;
        this.notifyAll();
    }
}
