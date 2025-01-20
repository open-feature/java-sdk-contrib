package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.sdk.exceptions.GeneralError;

/**
 * A helper class to wait for events.
 */
public class Wait {
    private volatile boolean isFinished;

    /**
     * Create a new Wait object.
     */
    public Wait() {}

    private Wait(boolean isFinished) {
        this.isFinished = isFinished;
    }

    /**
     * Blocks the calling thread until either {@link Wait#onFinished()} is called or the deadline is exceeded, whatever
     * happens first.
     * If the deadline is exceeded, a GeneralError will be thrown.
     *
     * @param deadline the maximum time in ms to wait
     * @throws GeneralError when the deadline is exceeded before {@link Wait#onFinished()} is called on this object
     */
    public void waitUntilFinished(long deadline) {
        long start = System.currentTimeMillis();
        long end = start + deadline;
        while (!isFinished) {
            long now = System.currentTimeMillis();
            // if wait(0) is called, the thread would wait forever, so we abort when this would happen
            if (now >= end) {
                throw new GeneralError(String.format(
                        "Deadline exceeded. Condition did not complete within the %d ms deadline", deadline));
            }
            long remaining = end - now;
            synchronized (this) {
                if (isFinished) { // might have changed in the meantime
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
    }

    /**
     * Wake up all threads that have called {@link Wait#waitUntilFinished(long)}.
     */
    public void onFinished() {
        synchronized (this) {
            isFinished = true;
            this.notifyAll();
        }
    }

    /**
     * Create a new Wait object that is already finished. Calls to {@link Wait#waitUntilFinished(long)} will return
     * immediately.
     *
     * @return an already finished Wait object
     */
    public static Wait finished() {
        return new Wait(true);
    }
}
