package dev.openfeature.contrib.providers.flagd.strategy;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;

final public class ResolveFactory {
    public static ResolveStrategy getStrategy(FlagdOptions options) {
        if (options.getOpenTelemetry() != null) {
            return new TracedResolving(options.getOpenTelemetry());
        }
        return new SimpleResolving();
    }
}
