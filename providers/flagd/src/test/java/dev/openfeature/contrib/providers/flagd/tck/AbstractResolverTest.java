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

/**
 * Shared configuration for running the OpenFeature Provider TCK against the flagd provider.
 *
 * <p>flagd resolves flags in two quite different ways, and both are worth conforming: RPC evaluates
 * remotely over gRPC, while in-process syncs the ruleset and evaluates locally. They share a backend
 * stack and differ only in resolver and port, so the modes are two small subclasses.
 *
 * <p>RPC passes the whole suite. In-process fails one scenario, and only one: see
 * {@link InProcessTest#knownDeviations()}. Three scenarios used to fail in both modes on flags the
 * testbed did not serve; flagd-testbed v3.11.0 ships them (open-feature/flagd-testbed#392) and they
 * pass.
 *
 * <p>A run occasionally carries one failure beyond those, of a shape that moves between runs. That
 * is the flagd-testbed readiness window of open-feature/flagd-testbed#394, and it is deliberately
 * not papered over with a sleep here; repeat a run before treating an extra failure as a regression.
 */
abstract class AbstractResolverTest extends ContainerizedProviderTckTest {

    /**
     * A port nothing listens on, for the initialisation-failure scenarios.
     *
     * <p>Deliberately not a port on the Compose stack: the stack must stay up for the whole suite,
     * and simulated outages belong to the control API.
     */
    private static final int UNAVAILABLE_PORT = 9999;

    /**
     * gRPC deadline for a provider that is expected to connect.
     *
     * <p>Generous on purpose, and measured. flagd <em>doubles</em> this value to get its
     * initialisation deadline, and the in-process resolver must sync the entire ruleset before it
     * reports ready. At 5000 the first two in-process scenarios failed reproducibly with
     * {@code Initialization timeout exceeded ... 10000 ms deadline} out of
     * {@code FlagdProviderSyncResources.waitForInitialization}; 15000 cleared that. Not a
     * post-command settle in disguise: a pause after the control call was tried at 50ms and at
     * 3000ms and fixed nothing, because the wait this covers is the provider's own initialisation.
     *
     * <p>Raising it further is not the fix for what remains. On a loaded host the first
     * {@code errors.feature} scenario can still error in in-process mode against the doubled
     * deadline, and only in the mode that must receive the whole ruleset after the first
     * {@code POST /start} — stack-side readiness, open-feature/flagd-testbed#394. So the bound stays
     * at 15000.
     */
    private static final int CONNECTED_DEADLINE_MS = 15000;

    /**
     * gRPC deadline for a provider pointed at a dead port.
     *
     * <p>Short on purpose, and deliberately not {@link #CONNECTED_DEADLINE_MS}: the
     * initialisation-failure scenarios assert that the failure is reported <em>promptly</em>, so a
     * provider that took as long to give up as it does to connect would defeat the point.
     */
    private static final int UNAVAILABLE_DEADLINE_MS = 1000;

    /** The resolver under test. */
    protected abstract Config.Resolver resolver();

    /** The container-internal port that resolver connects to. */
    protected abstract int backendPort();

    /**
     * {@inheritDoc}
     *
     * <p>The flagd-testbed's own stack, from the {@code test-harness} submodule the legacy e2e suites
     * also run, so the image tag follows the submodule pin rather than a second copy of it. A
     * hand-maintained compose file beside it was the alternative, and it drifted: it still named
     * v3.10.3 while the submodule had moved on, which is exactly the comparison this suite cannot
     * afford to get wrong.
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
        return new FlagdProvider(baseOptions()
                .deadline(CONNECTED_DEADLINE_MS)
                .host(endpoint.host())
                .port(endpoint.port(backendPort()))
                .build());
    }

    @Override
    public FeatureProvider createUnavailableProvider() {
        return new FlagdProvider(baseOptions()
                .deadline(UNAVAILABLE_DEADLINE_MS)
                .host("localhost")
                .port(UNAVAILABLE_PORT)
                .build());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Everything declarable except {@link Capability#REINITIALIZATION}, each declaration on
     * evidence from a run or from the provider's source rather than inherited from the "everything
     * except" default. Measured on v3.11.0: 65 scenarios and 2 skipped in both modes — the withheld
     * {@code @reinitialization}, and {@code @large-integers}, which the SDK cannot ask — with 63
     * passing on RPC and 62 on in-process, whose one failure is
     * {@link InProcessTest#knownDeviations()}.
     *
     * <p>{@link Capability#NUMERIC_COERCION} is declared on both, and is where the two modes now
     * differ: RPC evaluates in flagd, which enforces the lossy half since v0.18.1, while in-process
     * evaluates here and does not.
     *
     * <p>{@link Capability#STRING_TYPING} and {@link Capability#FULLY_TYPED_VALUES} are both declared,
     * and all four of their scenarios pass in both modes. flagd's flag definitions carry a JSON type
     * per flag and both resolvers preserve it — for a float and a structure as much as for a boolean
     * and an integer — so nothing about this backend is partially typed and the second tag is declared
     * rather than withheld.
     *
     * <p>{@link Capability#LIFECYCLE} is declared because flagd reaches its backend during
     * initialisation in both modes — an RPC round trip, or a full ruleset sync — so its scenarios
     * assert something real here rather than passing vacuously.
     *
     * <p>{@link Capability#REINITIALIZATION} is withheld, and that is a fact about the provider rather
     * than a defect in it. {@code shutdown()} never clears {@code isInitialized}
     * (FlagdProvider.java:136-155, FlagdProviderSyncResources.java:27-28), so a later
     * {@code initialize()} returns at its first check (FlagdProvider.java:121-125) without rebuilding
     * the resolver, the {@code shutdownNow()}'d RPC channel, the terminated retry scheduler or the
     * {@code final errorExecutor}. A shut-down flagd provider is terminally shut down.
     *
     * <p>{@link Capability#STALE} is declared for both resolvers: {@code PROVIDER_STALE} is emitted
     * from {@code FlagdProvider.onError} (FlagdProvider.java:258-264), which the shared
     * {@code onProviderEvent} switch reaches from either resolver (FlagdProvider.java:197, 236), so
     * the emit sits in the provider layer and not in a transport.
     *
     * <p>{@link Capability#declarableExcept} and not {@code EnumSet.complementOf}, whose complement
     * would include reserved tags the suite refuses at startup.
     */
    @Override
    public Set<Capability> capabilities() {
        return Capability.declarableExcept(Capability.REINITIALIZATION);
    }

    private FlagdOptions.FlagdOptionsBuilder baseOptions() {
        return FlagdOptions.builder()
                .resolverType(resolver())
                .retryGracePeriod(2)
                .retryBackoffMs(500);
    }
}
