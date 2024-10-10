package dev.openfeature.contrib.providers.flagd.resolver.common.backoff;

import lombok.Getter;

/**
 * This strategy will backoff for a fixed number of retries before being exhausted.
 * The backoff time is determined by the provided {@link BackoffStrategy}.
 */
public class NumberOfRetriesBackoff implements BackoffStrategy {
    private final int numRetries;
    private final BackoffStrategy backoffStrategy;

    @Getter
    private int retryCount;

    /**
     * Creates a new backoff strategy that will backoff for a fixed number of retries before being exhausted.
     * The backoff time is determined by the provided {@link BackoffStrategy}.
     *
     * @param numRetries the number of retries before the backoff is exhausted
     * @param backoffStrategy the backoff strategy to use for determining the backoff time
     */
    public NumberOfRetriesBackoff(int numRetries, BackoffStrategy backoffStrategy) {
        this.numRetries = numRetries;
        this.backoffStrategy = backoffStrategy;
        this.retryCount = 0;
    }

    @Override
    public long getCurrentBackoffMillis() {
        return backoffStrategy.getCurrentBackoffMillis();
    }

    @Override
    public boolean isExhausted() {
        return retryCount >= numRetries;
    }

    @Override
    public void nextBackoff() {
        if (isExhausted()) {
            return;
        }

        retryCount++;
        backoffStrategy.nextBackoff();
    }

    @Override
    public void reset() {
        retryCount = 0;
        backoffStrategy.reset();
    }
}
