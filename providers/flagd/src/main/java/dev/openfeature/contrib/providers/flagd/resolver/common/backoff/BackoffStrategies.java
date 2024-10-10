package dev.openfeature.contrib.providers.flagd.resolver.common.backoff;

/**
 * A factory class for creating common backoff strategies.
 */
public class BackoffStrategies {
    private BackoffStrategies() {
    }

    public static BackoffStrategy exponentialTimeBackoff(long initialBackoffMillis) {
        return new ExponentialTimeBackoff(initialBackoffMillis);
    }

    public static BackoffStrategy exponentialTimeBackoff(long initialBackoffMillis, long maxBackoffMillis) {
        return new ExponentialTimeBackoff(initialBackoffMillis, maxBackoffMillis);
    }

    public static BackoffStrategy constantTimeBackoff(long millis) {
        return new ConstantTimeBackoff(millis);
    }

    public static BackoffStrategy noBackoff() {
        return new ConstantTimeBackoff(0L);
    }

    public static BackoffStrategy maxRetriesWithExponentialTimeBackoffStrategy(int maxRetries,
                                                                               long initialBackoffMillis) {
        return new NumberOfRetriesBackoff(maxRetries, exponentialTimeBackoff(initialBackoffMillis));
    }
}
