package dev.openfeature.contrib.providers.flagd.tck;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.tools.tck.BackendEndpoint;
import java.util.function.Consumer;

/**
 * What the flagd extension scenarios under {@code src/test/resources/extensions} need from the
 * adoption beyond the TCK's own harness.
 *
 * <p>The TCK builds every provider through {@code createProvider()} with no arguments, which suits
 * the canonical scenarios: they ask the same question of every provider. The extensions port the
 * flagd-testbed scenarios, which configure the provider per scenario (a cache, a selector, a grace
 * period). Rather than a second way of creating a provider, a scenario records its configuration
 * here and the TCK's own {@code Given a stable provider} applies it.
 *
 * <p>Step classes receive the harness by injection and cast it to this interface.
 */
public interface FlagdTckHarness {

    /** The resolver this suite runs, for gating {@code @rpc} and {@code @in-process} scenarios. */
    Config.Resolver resolver();

    /**
     * Records a change to the options of the next provider this scenario creates.
     *
     * <p>Applied after the adoption's defaults, in the order recorded, so a scenario can override
     * any of them. Cleared before every scenario.
     *
     * @param customization a change to the options builder
     */
    void customize(Consumer<FlagdOptions.FlagdOptionsBuilder> customization);

    /** Discards every recorded customization. */
    void resetCustomizations();

    /** The running Compose stack, for steps that need a port the adoption does not use itself. */
    BackendEndpoint backendEndpoint();

    /** Base URL of the flagd-testbed launchpad control API, without a trailing slash. */
    String launchpadUrl();
}
