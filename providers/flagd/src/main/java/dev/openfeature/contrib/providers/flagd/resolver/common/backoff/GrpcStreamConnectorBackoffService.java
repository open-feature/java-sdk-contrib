package dev.openfeature.contrib.providers.flagd.resolver.common.backoff;

/**
 * Backoff service that supports "silent" backoff.
 */
public class GrpcStreamConnectorBackoffService extends BackoffService {
    private final BackoffStrategy silentRecoverBackoff;

    /**
     * Create a new backoff service that will not backoff (0ms) on first attempt.
     * Subsequent attempts will backoff exponentially.
     *
     * @param initialBackoffMillis initial backoff time in milliseconds used for exponential error backoff
     */
    public GrpcStreamConnectorBackoffService(long initialBackoffMillis) {
        this(BackoffStrategies.exponentialTimeBackoff(initialBackoffMillis));
    }

    /**
     * Create a new backoff service that will not backoff (0ms) on first attempt.
     * Subsequent attempts will backoff using the provided backoff strategy.
     *
     * @param errorBackoff backoff strategy to use after the first attempt
     */
    public GrpcStreamConnectorBackoffService(BackoffStrategy errorBackoff) {
        this(new NumberOfRetriesBackoff(1, BackoffStrategies.noBackoff()), errorBackoff);
    }

    private GrpcStreamConnectorBackoffService(BackoffStrategy silentRecoverBackoff, BackoffStrategy errorBackoff) {
        super(new CombinedBackoff(new BackoffStrategy[]{
            silentRecoverBackoff,
            errorBackoff
        }));
        this.silentRecoverBackoff = silentRecoverBackoff;
    }

    public boolean shouldRetrySilently() {
        return ((CombinedBackoff) getStrategy()).getCurrentStrategy() == silentRecoverBackoff;
    }
}
