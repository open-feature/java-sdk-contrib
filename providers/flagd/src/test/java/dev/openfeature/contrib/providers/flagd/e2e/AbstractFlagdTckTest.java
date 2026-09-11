package dev.openfeature.contrib.providers.flagd.e2e;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.tools.providertck.BackendEndpoint;
import dev.openfeature.contrib.tools.providertck.Capability;
import dev.openfeature.contrib.tools.providertck.ContainerizedProviderTckTest;
import dev.openfeature.contrib.tools.providertck.KnownDeviation;
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
 * <p>Each concrete subclass is its own JUnit suite and its own TCK harness; the TCK works out which
 * one is running from the JUnit test plan, so adding a mode needs no registration or build
 * configuration.
 *
 * <p><strong>The testbed does not yet serve the whole canonical flag set.</strong> Three of the
 * flags the suite's assets added are absent from {@code flagd-testbed} v3.8.0:
 * {@code large-integer-flag}, {@code huge-integer-flag} and {@code integral-float-flag}. Only the
 * first is reached — {@code huge-integer-flag} is asked for solely under {@code @large-integers},
 * which is not applicable in Java, and {@code integral-float-flag} solely under
 * {@code @numeric-coercion}, which is withheld below — so exactly one untagged scenario, the 32-bit
 * precision one, fails with {@code FLAG_NOT_FOUND} in both modes until
 * open-feature/flagd-testbed#392 lands and the tag here is bumped.
 *
 * <p>The three falsy flags used to fail the same way and no longer do. The testbed's
 * {@code zero-flags.json} already served {@code boolean-zero-flag}, {@code integer-zero-flag} and
 * {@code string-zero-flag} with {@code zero}/{@code non-zero} variants, while the canonical set
 * called them {@code false-flag}, {@code zero-flag} and {@code empty-string-flag}; spec ba002ce8
 * renamed the canonical flags to the testbed's names rather than the other way round, so those
 * three scenarios now resolve against flags that were always there.
 *
 * <p>A missing flag is a gap in the stack, not in the provider, so it is recorded here rather than
 * declared as a {@link KnownDeviation}: a deviation says the provider is wrong, and the provider was
 * never given the flag to get wrong.
 */
abstract class AbstractFlagdTckTest extends ContainerizedProviderTckTest {

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
     * <p>Generous on purpose. flagd derives its initialisation deadline from this value, and the
     * in-process resolver must sync the entire ruleset before it reports ready — which intermittently
     * takes longer than a deadline tuned for a single RPC round trip.
     */
    private static final int CONNECTED_DEADLINE_MS = 5000;

    /**
     * gRPC deadline for a provider pointed at a dead port.
     *
     * <p>Short on purpose, and deliberately not the same as {@link #CONNECTED_DEADLINE_MS}: the
     * initialisation-failure scenarios assert that the failure is reported <em>promptly</em>, so a
     * provider that takes as long to give up as it does to connect would defeat the point.
     */
    private static final int UNAVAILABLE_DEADLINE_MS = 1000;

    /** The resolver under test. */
    protected abstract Config.Resolver resolver();

    /** The container-internal port that resolver connects to. */
    protected abstract int backendPort();

    @Override
    public File composeFile() {
        return new File("src/test/resources/tck/docker-compose.yaml");
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
     * <p>Everything declarable except {@link Capability#NUMERIC_COERCION}. Evaluating
     * {@code float-flag} (0.5) through the integer API returns {@code 0} with <em>no</em> error code
     * rather than {@code TYPE_MISMATCH} with the code default — the value is silently truncated.
     * Coercion as such is permitted, and the capability says so: the rule is that a lossless
     * coercion must succeed and a lossy one must fail. It is the lossy case being accepted that is a
     * defect to fix, not a design choice; this override should be deleted once it is.
     *
     * <p>Declared here rather than per mode because both resolvers behave identically, which places
     * the defect in the shared provider layer rather than in either transport. Every other
     * capability, including the full non-numeric type-mismatch matrix, holds in both modes.
     *
     * <p>That includes {@link Capability#LIFECYCLE}, and legitimately so: flagd reaches its backend
     * during initialisation in both modes — an RPC round trip, or a full ruleset sync — so the
     * lifecycle scenarios assert something real here rather than passing vacuously.
     *
     * <p>{@link Capability#declarableExcept} rather than {@code EnumSet.complementOf}, which is what
     * this used to be. The complement of one capability is every other <em>enum constant</em>,
     * including {@code @targeting} and {@code @caching} — reserved tags no scenario carries — so
     * declaring the complement claimed two capabilities nothing had examined. It would now also
     * claim {@link Capability#LARGE_INTEGERS}, which no Java provider can have — the SDK's integer
     * accessor is a 32-bit {@code Integer} — and the suite refuses such a declaration at startup.
     * {@code declarableExcept} leaves the not-applicable tag out on its own, and its one scenario is
     * reported as skipped with that reason on every run.
     */
    @Override
    public Set<Capability> capabilities() {
        return Capability.declarableExcept(Capability.NUMERIC_COERCION);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The withheld {@link Capability#NUMERIC_COERCION} is a defect, not a limitation, and
     * something has to say so. In the results the two are indistinguishable: the scenario is skipped
     * either way, and the declaration explains only <em>that</em> the capability was not claimed,
     * never whether flagd chose not to claim it. A consumer comparing providers would otherwise read
     * this exactly as it reads a provider with no streaming transport declining
     * {@code @configuration-change}, which is a decision rather than a bug.
     *
     * <p>Tracked against flagd's numeric coercion ADR, which is where the rule this deviates from is
     * settled: coercion is permitted when it is lossless and must fail with {@code TYPE_MISMATCH}
     * only when information would be lost. The summary says which half is broken, because "flagd
     * coerces numbers" on its own reads as a description of intended behaviour. Delete the entry —
     * and the {@code capabilities()} override above — once the lossy case reports
     * {@code TYPE_MISMATCH}.
     */
    @Override
    public List<KnownDeviation> knownDeviations() {
        return Collections.singletonList(KnownDeviation.tracked(
                Capability.NUMERIC_COERCION,
                "https://github.com/open-feature/flagd/issues/1996",
                "The lossy half of the coercion rule is not enforced: evaluating float-flag (0.5) "
                        + "through the integer API returns 0 with no error code, rather than "
                        + "TYPE_MISMATCH with the code default, so the fractional part is discarded "
                        + "silently. Lossless coercion is permitted and is not the defect. Both "
                        + "resolvers behave identically, which places it in the shared provider layer "
                        + "rather than in either transport."));
    }

    private FlagdOptions.FlagdOptionsBuilder baseOptions() {
        return FlagdOptions.builder()
                .resolverType(resolver())
                .retryGracePeriod(2)
                .retryBackoffMs(500);
    }
}
