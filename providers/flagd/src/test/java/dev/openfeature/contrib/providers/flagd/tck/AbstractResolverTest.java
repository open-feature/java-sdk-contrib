package dev.openfeature.contrib.providers.flagd.tck;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.tools.tck.BackendEndpoint;
import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.ContainerizedProviderTckTest;
import dev.openfeature.sdk.FeatureProvider;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Shared configuration for running the OpenFeature Provider TCK against the flagd provider.
 *
 * <p>flagd resolves flags in two quite different ways, and both are worth conforming: RPC evaluates
 * remotely over gRPC, while in-process syncs the ruleset and evaluates locally. They share a backend
 * stack and differ only in resolver and port, so the modes are two small subclasses.
 *
 * <p>An occasional extra failure of a shape that moves between runs is the flagd-testbed readiness
 * window of open-feature/flagd-testbed#394, not a regression; repeat the run.
 */
abstract class AbstractResolverTest extends ContainerizedProviderTckTest implements FlagdTckHarness {

    /** A port nothing listens on; an outage on the Compose stack belongs to the control API. */
    private static final int UNAVAILABLE_PORT = 9999;

    /**
     * gRPC deadline for a provider that is expected to connect.
     *
     * <p>flagd <em>doubles</em> it, and in-process must sync the entire ruleset before it reports
     * ready; 5000 was not enough for that. Raising it past 15000 does not help what remains, which is
     * stack-side readiness (open-feature/flagd-testbed#394).
     */
    private static final int CONNECTED_DEADLINE_MS = 15000;

    /** gRPC deadline for a dead port; these scenarios assert that the failure is reported promptly. */
    private static final int UNAVAILABLE_DEADLINE_MS = 1000;

    /** Per-scenario option changes recorded by the extension steps; see {@link FlagdTckHarness}. */
    private final List<Consumer<FlagdOptions.FlagdOptionsBuilder>> customizations = new CopyOnWriteArrayList<>();

    /** The resolver under test. */
    @Override
    public abstract Config.Resolver resolver();

    /** The container-internal port that resolver connects to. */
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

    @Override
    public List<Integer> backendPorts() {
        return Collections.singletonList(backendPort());
    }

    @Override
    public FeatureProvider createProvider(BackendEndpoint endpoint) {
        return new FlagdProvider(customized(baseOptions()
                        .deadline(CONNECTED_DEADLINE_MS)
                        .host(endpoint.host())
                        .port(endpoint.port(backendPort())))
                .build());
    }

    @Override
    public FeatureProvider createUnavailableProvider() {
        return new FlagdProvider(customized(baseOptions()
                        .deadline(UNAVAILABLE_DEADLINE_MS)
                        .host("localhost")
                        .port(UNAVAILABLE_PORT))
                .build());
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
     * terminally shut down.
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
