package dev.openfeature.contrib.providers.flagd.resolver.common.backoff;

/**
 * A backoff strategy that exponentially increases the backoff time.
 * This backoff is never exhausted.
 */
public class ExponentialTimeBackoff implements BackoffStrategy {
    private final long initialBackoff;
    private final long maxBackoff;
    private long currentBackoff;

    /**
     * A backoff strategy that exponentially increases the backoff time.
     * This backoff will increase the backoff time by a factor of 2.
     *
     * @param initialBackoffMillis the initial backoff time in milliseconds
     */
    public ExponentialTimeBackoff(long initialBackoffMillis) {
        this(initialBackoffMillis, Long.MAX_VALUE);
    }

    /**
     * A backoff strategy that exponentially increases the backoff time.
     * This backoff will increase the backoff time by a factor of 2 until the maximum backoff time is reached.
     * It is never exhausted but will stale at the maximum backoff time.
     *
     * @param initialBackoffMillis the initial backoff time in milliseconds
     * @param maxBackoffMillis the maximum backoff time in milliseconds
     */
    public ExponentialTimeBackoff(long initialBackoffMillis, long maxBackoffMillis) {
        this.initialBackoff = initialBackoffMillis;
        this.maxBackoff = maxBackoffMillis;
        reset();
    }

    @Override
    public long getCurrentBackoffMillis() {
        return currentBackoff;
    }

    @Override
    public boolean isExhausted() {
        return false;
    }

    @Override
    public void nextBackoff() {
        currentBackoff = Math.min(currentBackoff * 2, maxBackoff);
    }

    @Override
    public void reset() {
        currentBackoff = Math.min(initialBackoff, maxBackoff);
    }
}
