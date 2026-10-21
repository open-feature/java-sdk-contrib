package dev.openfeature.contrib.providers.flagd.tck;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.tools.tck.BackendEndpoint;
import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.ContainerizedProviderTckTest;
import dev.openfeature.sdk.FeatureProvider;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Shared configuration for running the OpenFeature Provider TCK against the flagd provider.
 *
 * <p>flagd resolves flags in three quite different ways, and all three are worth conforming: RPC
 * evaluates remotely over gRPC, in-process syncs the ruleset and evaluates locally, and file reads
 * the ruleset off the disk. All three share a backend stack; the two gRPC modes differ only in
 * resolver and port, and {@link FileTest} says what the third needs instead.
 */
abstract class AbstractResolverTest extends ContainerizedProviderTckTest implements FlagdTckHarness {

    /** A port nothing listens on; an outage on the Compose stack belongs to the control API. */
    private static final int UNAVAILABLE_PORT = 9999;

    /**
     * gRPC deadline for a provider that is expected to connect.
     *
     * <p>flagd <em>doubles</em> it, and in-process must sync the entire ruleset before it reports
     * ready; 5000 was not enough for that.
     */
    private static final int CONNECTED_DEADLINE_MS = 15000;

    /** gRPC deadline for a dead port; these scenarios assert that the failure is reported promptly. */
    private static final int UNAVAILABLE_DEADLINE_MS = 1000;

    /** Per-scenario option changes recorded by the extension steps; see {@link FlagdTckHarness}. */
    private final List<Consumer<FlagdOptions.FlagdOptionsBuilder>> customizations = new CopyOnWriteArrayList<>();

    /** The resolver under test. */
    public abstract Config.Resolver resolver();

    /**
     * The container-internal port this mode waits on before the first scenario.
     *
     * <p>The two gRPC resolvers dial it. {@link FileTest} does not, and names flagd's sync listener
     * only because a flagd that is listening is a flagd that has written its flag file.
     */
    protected abstract int backendPort();

    /**
     * {@inheritDoc}
     *
     * <p>The flagd-testbed's own stack, from the {@code test-harness} submodule the legacy e2e suites
     * also run, so the image tag follows the submodule pin instead of a second copy that can drift.
     */
    @Override
    public File composeFile() {
        return new File("test-harness/docker-compose.yaml");
    }

    /** The testbed's compose file names the flagd service {@code flagd}. */
    @Override
    public String backendService() {
        return "flagd";
    }

    /** The envoy sidecar's two listeners, which the extension scenarios connect through. */
    @Override
    public Map<String, List<Integer>> additionalPorts() {
        return Collections.singletonMap(ENVOY_SERVICE, Arrays.asList(ENVOY_ROUTING_PORT, ENVOY_FORBIDDEN_PORT));
    }

    @Override
    public List<Integer> backendPorts() {
        return Collections.singletonList(backendPort());
    }

    @Override
    public FeatureProvider createProvider(BackendEndpoint endpoint) {
        return new FlagdProvider(customized(flagSource(baseOptions().deadline(CONNECTED_DEADLINE_MS), endpoint))
                .build());
    }

    @Override
    public FeatureProvider createUnavailableProvider() {
        return new FlagdProvider(customized(missingFlagSource(baseOptions().deadline(UNAVAILABLE_DEADLINE_MS)))
                .build());
    }

    /**
     * Points the options at the running stack; a resolver that reads no socket overrides this.
     *
     * @param options the options being built, with the connected deadline already set
     * @param endpoint host and mapped ports of the running stack
     * @return the same builder, told where to find the flags
     */
    protected FlagdOptions.FlagdOptionsBuilder flagSource(
            FlagdOptions.FlagdOptionsBuilder options, BackendEndpoint endpoint) {
        return options.host(endpoint.host()).port(endpoint.port(backendPort()));
    }

    /**
     * Points the options at a flag source that is not there, for the {@code @unavailable} scenarios.
     *
     * @param options the options being built, with the short deadline already set
     * @return the same builder, told to look somewhere nothing answers
     */
    protected FlagdOptions.FlagdOptionsBuilder missingFlagSource(FlagdOptions.FlagdOptionsBuilder options) {
        return options.host("localhost").port(UNAVAILABLE_PORT);
    }

    @Override
    public void customize(Consumer<FlagdOptions.FlagdOptionsBuilder> customization) {
        customizations.add(customization);
    }

    @Override
    public void resetCustomizations() {
        customizations.clear();
    }

    @Override
    public BackendEndpoint backendEndpoint() {
        return endpoint();
    }

    @Override
    public String launchpadUrl() {
        return "http://" + endpoint().host() + ":" + endpoint().port(controlPort());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Everything declarable except {@link Capability#REINITIALIZATION}, which is a fact about the
     * provider rather than a defect in it: {@code shutdown()} never clears {@code isInitialized}, so a
     * later {@code initialize()} returns at its first check without rebuilding the resolver, the
     * {@code shutdownNow()}'d channel or the terminated executors. A shut-down flagd provider is
     * terminally shut down. {@link FileTest} narrows this further, for the reason it gives.
     *
     * <p>{@link Capability#declarableExcept} and not {@code EnumSet.complementOf}, whose complement
     * would include reserved tags the suite refuses at startup.
     */
    @Override
    public Set<Capability> capabilities() {
        return Capability.declarableExcept(Capability.REINITIALIZATION);
    }

    private FlagdOptions.FlagdOptionsBuilder customized(FlagdOptions.FlagdOptionsBuilder builder) {
        customizations.forEach(customization -> customization.accept(builder));
        return builder;
    }

    private FlagdOptions.FlagdOptionsBuilder baseOptions() {
        return FlagdOptions.builder()
                .resolverType(resolver())
                .retryGracePeriod(2)
                .retryBackoffMs(500);
    }
}
