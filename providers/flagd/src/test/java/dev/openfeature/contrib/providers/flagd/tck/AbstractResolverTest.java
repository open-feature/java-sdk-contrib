package dev.openfeature.contrib.providers.flagd.tck;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.tools.tck.BackendEndpoint;
import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.ContainerizedProviderTckTest;
import dev.openfeature.contrib.tools.tck.KnownDeviation;
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
 * <p><strong>Three scenarios fail in both modes on flags the pinned testbed image does not serve</strong>
 * (open-feature/flagd-testbed#392 names them and says what each catches): the untagged 32-bit
 * precision scenario and the {@code @variants} row asking for {@code large-integer-flag}'s
 * {@code max-int32}, both answered as if the flag were absent, and the {@code @numeric-coercion}
 * scenario "An integral float requested as an integer is coerced without loss". The third is the
 * cost of declaring {@code @numeric-coercion} — see {@link #knownDeviations()}. None of the three is
 * a {@link KnownDeviation}: the provider was never given the flag to get wrong.
 *
 * <p><strong>A run occasionally carries one failure beyond those, and it is the stack's.</strong>
 * Observed in RPC mode as {@code FLAG_NOT_FOUND} on an {@code evaluation.feature} row that expected
 * no error code; the next run of the same tree was clean. That is the flagd-testbed readiness window
 * of open-feature/flagd-testbed#394, the same intermittency the OFREP adoption records, and it is
 * not covered with a sleep here either. Repeat a run before treating an extra failure as a
 * regression — anything that reproduces is one.
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
     * reports ready. At 5000 the first two in-process scenarios failed <em>reproducibly</em> with
     * {@code Initialization timeout exceeded; did not complete within the 10000 ms deadline} out of
     * {@code FlagdProviderSyncResources.waitForInitialization}, on flagd-testbed v3.8.0 and v3.10.1
     * alike; 15000 cleared that. Not a post-command settle in disguise: a pause after the control
     * call was tried at 50ms and at 3000ms and fixed nothing, because the wait this covers is the
     * provider's own initialisation. {@link #UNAVAILABLE_DEADLINE_MS} stays short so the promptness
     * assertions still mean something.
     *
     * <p><strong>Not fully solved, and the bound is not the thing to keep raising.</strong> On a
     * loaded Docker-in-WSL host the <em>first</em> scenario of {@code errors.feature} still errors
     * in in-process mode against the doubled 30000 ms deadline, after some 53 seconds of wall clock.
     * Measured three times in a row, and it reproduces with {@code @numeric-coercion} withheld
     * exactly as with it declared, so it is not a consequence of what this suite declares. RPC mode
     * never shows it. That shape is stack-side readiness rather than provider slowness — only the
     * mode that has to receive the whole ruleset after the first {@code POST /start} is affected,
     * which is the defect open-feature/flagd-testbed#394 exists to close. So the bound stays at
     * 15000 and this is recorded rather than covered.
     */
    private static final int CONNECTED_DEADLINE_MS = 15000;

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

    /**
     * {@inheritDoc}
     *
     * <p>Outside this module on purpose, and not the idiomatic {@code src/test/resources} path: the
     * OFREP adoption runs against the same stack and names the same file, so there is one image tag
     * for both rather than two that can drift. Module-relative, like any other value here.
     */
    @Override
    public File composeFile() {
        return new File("../../tools/flagd-testbed/docker-compose.yaml");
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
     * <p>Everything declarable except {@link Capability#REINITIALIZATION}. Each declaration below is
     * on evidence from a run or from the provider's source, not by inheriting the "everything
     * except" default. Measured on the pinned image, both modes: 65 scenarios, 59 passing, 2 skipped
     * — the withheld {@code @reinitialization}, and {@code @large-integers}, which the SDK cannot
     * ask — and 4 failing, the one real defect plus the three testbed gaps above. The cold-start
     * error on {@link #CONNECTED_DEADLINE_MS} did not reproduce in that run; when it appears
     * in-process it costs one further scenario.
     *
     * <p><strong>{@link Capability#NUMERIC_COERCION} is declared even though one of its scenarios
     * fails</strong> — see {@link #knownDeviations()}. Evaluating {@code float-flag} (0.5) through
     * the integer API returns {@code 0} with <em>no</em> error code rather than
     * {@code TYPE_MISMATCH} with the code default, so the fractional part is discarded silently.
     * Measured in both modes: of the tag's three scenarios, "An integer requested as a float is
     * widened without loss" <em>passes</em>, which is the fact that settles the shape of the report,
     * because a provider that performs the coercion and gets one direction wrong is not one that
     * declines to coerce. The lossy scenario fails; the remaining lossless one fails only on the
     * absent {@code integral-float-flag}. Both resolvers behave identically, which places the defect
     * in the shared provider layer rather than in either transport — as does every other capability
     * here, including the full non-numeric type-mismatch matrix.
     *
     * <p>{@link Capability#LIFECYCLE} is declared because flagd reaches its backend during
     * initialisation in both modes — an RPC round trip, or a full ruleset sync — so the lifecycle
     * scenarios assert something real here rather than passing vacuously.
     *
     * <p><strong>{@link Capability#REINITIALIZATION} is withheld, and that is a fact about the
     * provider rather than a defect in it.</strong> {@code shutdown()} sets the sync resources' own
     * {@code isShutDown} flag and never clears {@code isInitialized}
     * (FlagdProvider.java:136-155, FlagdProviderSyncResources.java:27-28, 112-115), so a later
     * {@code initialize()} returns at its first check without rebuilding anything
     * (FlagdProvider.java:121-125): the resolver is shut down, the RPC channel was
     * {@code shutdownNow()}'d, the retry scheduler is terminated and {@code errorExecutor} is a
     * {@code final} field nothing re-creates. A shut-down flagd provider is terminally shut down.
     * Requirement 2.5.2 permits that, so withholding the tag is the whole of what is owed — see
     * {@link Capability#REINITIALIZATION} — and the one scenario it gates is skipped with this
     * reason on every run.
     *
     * <p>{@link Capability#STALE} is declared for both resolvers, and examined rather than inherited.
     * {@code PROVIDER_STALE} is emitted from {@code FlagdProvider.onError}
     * (FlagdProvider.java:258-264), which the shared {@code onProviderEvent} switch reaches on
     * {@code PROVIDER_ERROR} from either resolver (FlagdProvider.java:197, 236), before the grace
     * period turns it into {@code PROVIDER_ERROR} — so the emit sits in the provider layer and not in
     * a transport, and "Losing the backend makes the provider stale, regaining it makes it ready
     * again" passes in RPC mode as well as in-process.
     *
     * <p>{@link Capability#VARIANTS} and {@link Capability#TARGETING} are both declared. flagd names
     * the variant it served in every resolution, so seven of the {@code @variants} outline's eight
     * rows pass in both modes; the eighth is one of the testbed gaps above. {@code @targeting}'s
     * three scenarios resolve {@code targeting-key-flag} through flagd's own rule evaluation and all
     * three pass in both modes on the image already pinned.
     *
     * <p>{@link Capability#DISABLED_FLAGS} is declared, and measured: both resolvers substitute the
     * caller's default for a flag whose state is {@code DISABLED} and report no error code, so all
     * four rows of that outline pass in both modes. Both resolvers are told the flag is disabled —
     * the in-process one evaluates the ruleset locally, and the RPC one still decides locally what
     * to do with a response that carries no value — so the question {@link Capability#DISABLED_FLAGS}
     * gates on is answered here in both.
     *
     * <p>{@link Capability#STANDARD_REASONS} arrived by the {@code declarableExcept} default rather
     * than by a decision, which is why it was measured before being written down. All nine scenarios
     * of {@code reason.feature} pass in both modes, including the two composing with
     * {@link Capability#TARGETING} and {@link Capability#DISABLED_FLAGS}: {@code STATIC} for the
     * rule-less flags, {@code TARGETING_MATCH} and {@code DEFAULT} either side of
     * {@code targeting-key-flag}'s rule, {@code DISABLED} for a disabled flag, and {@code ERROR}
     * beside {@code FLAG_NOT_FOUND} and {@code TYPE_MISMATCH}.
     *
     * <p>{@link Capability#declarableExcept} and not {@code EnumSet.complementOf}: the complement of
     * one capability is every other <em>enum constant</em>, reserved tags included, and the suite
     * refuses such a declaration at startup.
     */
    @Override
    public Set<Capability> capabilities() {
        return Capability.declarableExcept(Capability.REINITIALIZATION);
    }

    /**
     * {@inheritDoc}
     *
     * <p>One entry, for {@link Capability#NUMERIC_COERCION}, taking the <strong>declared and
     * failing</strong> shape. Appendix F's declaring rule decides it, and both halves apply here in
     * order: flagd <em>is</em> attempting the coercion, which the widening scenario proves, and two
     * of the tag's three scenarios can be put to it. A provider that simply does not coerce would
     * stop at the first half and withhold, which is what the SDK's in-memory provider does and why
     * the self-tests in {@code tools/tck} skip these scenarios.
     *
     * <p>Declaring it costs one failure that is the stack's rather than flagd's, since
     * {@code integral-float-flag} is absent from the pinned image. Accepted, and the summary below
     * says so explicitly — a deviation that did not would have the report accuse flagd of the gap.
     *
     * <p>Tracked against flagd's numeric coercion ADR, which is where the rule this deviates from is
     * settled. The summary names which half is broken, because "flagd coerces numbers" on its own
     * reads as a description of intended behaviour. Delete the entry once the lossy case reports
     * {@code TYPE_MISMATCH}; the capability needs no change then.
     */
    @Override
    public List<KnownDeviation> knownDeviations() {
        return Collections.singletonList(KnownDeviation.tracked(
                Capability.NUMERIC_COERCION,
                "https://github.com/open-feature/flagd/issues/1996",
                "The lossy half of the coercion rule is not enforced: evaluating float-flag (0.5) "
                        + "through the integer API returns 0 with no error code, rather than "
                        + "TYPE_MISMATCH with the code default, so the fractional part is discarded "
                        + "silently. Lossless coercion is permitted and is not the defect -- flagd "
                        + "does widen an integer to a float correctly, which is why the capability is "
                        + "declared and the scenario left to fail rather than the capability "
                        + "withheld. Both resolvers behave identically, which places it in the shared "
                        + "provider layer rather than in either transport. The tag's third scenario "
                        + "also fails, but for an unrelated reason that is not flagd's: "
                        + "integral-float-flag is absent from the pinned flagd-testbed image, "
                        + "open-feature/flagd-testbed#392."));
    }

    private FlagdOptions.FlagdOptionsBuilder baseOptions() {
        return FlagdOptions.builder()
                .resolverType(resolver())
                .retryGracePeriod(2)
                .retryBackoffMs(500);
    }
}
