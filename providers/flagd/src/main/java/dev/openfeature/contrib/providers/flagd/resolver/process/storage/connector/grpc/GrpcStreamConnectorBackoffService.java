package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc;


import dev.openfeature.contrib.providers.flagd.resolver.common.backoff.BackoffService;
import dev.openfeature.contrib.providers.flagd.resolver.common.backoff.BackoffStrategies;
import dev.openfeature.contrib.providers.flagd.resolver.common.backoff.BackoffStrategy;
import dev.openfeature.contrib.providers.flagd.resolver.common.backoff.NumberOfRetriesBackoff;
import dev.openfeature.contrib.providers.flagd.resolver.common.backoff.CombinedBackoff;

class GrpcStreamConnectorBackoffService extends BackoffService {
    private static final int INIT_BACK_OFF = 2 * 1000;
    private static final int MAX_BACK_OFF = 120 * 1000;

    private final BackoffStrategy silentRecoverBackoff;

    /**
     * Create a new backoff service that will not backoff (0ms) on first attempt.
     * Subsequent attempts will backoff exponentially.
     *
     * @return a new instance of the backoff service
     */
    public static GrpcStreamConnectorBackoffService create() {
        // Try to recover silently on the first failure without backoff
        BackoffStrategy silentRecoverBackoff = new NumberOfRetriesBackoff(1, BackoffStrategies.noBackoff());

        // Backoff exponentially for subsequent failures
        BackoffStrategy errorRecoveryBackoff = BackoffStrategies.exponentialTimeBackoff(INIT_BACK_OFF, MAX_BACK_OFF);

        return new GrpcStreamConnectorBackoffService(silentRecoverBackoff, errorRecoveryBackoff);
    }

    private GrpcStreamConnectorBackoffService(BackoffStrategy silentRecoverBackoff, BackoffStrategy errorBackoff) {
        super(new CombinedBackoff(new BackoffStrategy[]{
            silentRecoverBackoff,
            errorBackoff
        }));
        this.silentRecoverBackoff = silentRecoverBackoff;
    }

    public boolean shouldRecoverSilently() {
        return ((CombinedBackoff) getStrategy()).getCurrentStrategy() == silentRecoverBackoff;
    }
}
