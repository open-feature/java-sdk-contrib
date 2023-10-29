package dev.openfeature.contrib.providers.flagd.resolver.grpc.strategy;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;

/**
 * Factory to create a ResolveStrategy.
 */
public final class ResolveFactory {
    /**
     * Factory method to initialize the resolving strategy.
     *
     * @param options Options.
     * @return the ResolveStrategy based on the provided options.
     */
    public static ResolveStrategy getStrategy(FlagdOptions options) {
        if (options.getOpenTelemetry() != null) {
            return new TracedResolving(options.getOpenTelemetry());
        }

        return new SimpleResolving();
    }
}
