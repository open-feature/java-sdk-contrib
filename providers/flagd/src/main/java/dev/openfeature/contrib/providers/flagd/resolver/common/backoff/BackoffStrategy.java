package dev.openfeature.contrib.providers.flagd.resolver.common.backoff;

/** A strategy interface for determining how long to backoff before retrying a failed operation. */
public interface BackoffStrategy {

    /**
     * The current backoff time in milliseconds. This value should be used to determine how long to
     * wait before retrying.
     *
     * @return the current backoff time in milliseconds
     */
    long getCurrentBackoffMillis();

    /**
     * Determines if the backoff strategy has been exhausted.
     *
     * @return true if the operation should backoff, false otherwise
     */
    boolean isExhausted();

    /** Move to the next backoff time. */
    void nextBackoff();

    /** Reset the backoff strategy to its initial state. */
    void reset();
}
