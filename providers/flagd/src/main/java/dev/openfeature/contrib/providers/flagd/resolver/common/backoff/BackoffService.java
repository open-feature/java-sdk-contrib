package dev.openfeature.contrib.providers.flagd.resolver.common.backoff;

import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;

/** A service that provides backoff functionality. */
public class BackoffService {
    public static final int DEFAULT_MAX_JITTER = 0x1 << 8; // 256; Random likes boundaries that are a power of 2

    @Getter
    private final BackoffStrategy strategy;

    @Getter
    private final int maxJitter;

    /**
     * Creates a new BackoffService with the given strategy and default maximum jitter. The default
     * maximum jitter is 256.
     *
     * @param strategy The backoff strategy to use
     */
    public BackoffService(BackoffStrategy strategy) {
        this(strategy, DEFAULT_MAX_JITTER);
    }

    /**
     * Creates a new BackoffService with the given strategy and maximum jitter.
     *
     * @param strategy The backoff strategy to use
     * @param maxJitter The maximum jitter value
     */
    public BackoffService(BackoffStrategy strategy, int maxJitter) {
        this.strategy = strategy;
        this.maxJitter = maxJitter;
    }

    /**
     * Returns the current backoff time in milliseconds. This backoff time will be used in
     * waitUntilNextAttempt.
     *
     * @return the current backoff time in milliseconds
     */
    public long getCurrentBackoffMillis() {
        return strategy.getCurrentBackoffMillis();
    }

    /**
     * Returns a random jitter value between 0 and maxJitter.
     *
     * @return a random jitter value
     */
    public long getRandomJitter() {
        if (maxJitter == 0) {
            return 0;
        }

        return ThreadLocalRandom.current().nextInt(maxJitter);
    }

    /** Resets the backoff strategy to its initial state. */
    public void reset() {
        strategy.reset();
    }

    /**
     * Returns whether the backoff strategy has more attempts left.
     *
     * @return true if the backoff strategy has more attempts left, false otherwise
     */
    public boolean shouldRetry() {
        return !strategy.isExhausted();
    }

    /**
     * Bolocks the current thread until the next attempt should be made. The time to wait is
     * determined by the backoff strategy and a random jitter.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void waitUntilNextAttempt() throws InterruptedException {
        long retryDelay = getCurrentBackoffMillis() + getRandomJitter();
        strategy.nextBackoff();

        Thread.sleep(retryDelay);
    }
}
