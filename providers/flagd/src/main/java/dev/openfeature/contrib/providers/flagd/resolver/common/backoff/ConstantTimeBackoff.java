package dev.openfeature.contrib.providers.flagd.resolver.common.backoff;

/**
 * A backoff strategy that always returns the same backoff time.
 * This backoff is never exhausted.
 */
public class ConstantTimeBackoff implements BackoffStrategy {
    final long millis;

    public ConstantTimeBackoff(long millis) {
        this.millis = millis;
    }

    @Override
    public long getCurrentBackoffMillis() {
        return millis;
    }

    @Override
    public boolean isExhausted() {
        return false;
    }

    @Override
    public void nextBackoff() {
    }

    @Override
    public void reset() {
    }
}
