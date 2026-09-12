package dev.openfeature.contrib.providers.flagd.e2e;

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
 * <p><strong>The testbed does not yet serve the whole canonical flag set.</strong> Three of the
 * flags the suite's assets added are absent from {@code flagd-testbed} v3.8.0:
 * {@code large-integer-flag}, {@code huge-integer-flag} and {@code integral-float-flag}. Two of the
 * three are reached — {@code huge-integer-flag} is asked for solely under {@code @large-integers},
 * which is withheld below for a reason of its own — so three scenarios fail with
 * {@code FLAG_NOT_FOUND} in both modes until open-feature/flagd-testbed#392 lands and the tag here
 * is bumped:
 *
 * <ul>
 *   <li>the untagged 32-bit precision scenario, which gets the code default instead of
 *       {@code 2147483647};
 *   <li>the {@code @variants} row asking for {@code large-integer-flag}'s {@code max-int32}, which
 *       is answered with no variant;
 *   <li>the {@code @numeric-coercion} scenario "An integral float requested as an integer is coerced
 *       without loss", which asks for {@code integral-float-flag}.
 * </ul>
 *
 * <p>The third of those is new here, and it is the cost of declaring {@code @numeric-coercion}
 * rather than withholding it — see {@link #knownDeviations()}, which explains why paying it is the
 * honest report.
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
     * <p>Generous on purpose, and measured. flagd derives its initialisation deadline from this
     * value — doubling it — and the in-process resolver must sync the entire ruleset before it
     * reports ready, which takes longer than a deadline tuned for a single RPC round trip.
     *
     * <p>At 5000 the first two in-process scenarios failed <em>reproducibly</em> on a slower host
     * with {@code Initialization timeout exceeded; did not complete within the 10000 ms deadline}
     * out of {@code FlagdProviderSyncResources.waitForInitialization}, on both flagd-testbed v3.8.0
     * and v3.10.1. Raising it to 15000 cleared that. The first scenario pays for a cold container as
     * well as for the sync, which is why it was the first two rather than all of them.
     *
     * <p>Worth being explicit that this is <strong>not</strong> a post-command settle in disguise.
     * A pause after the control call was tried at 50ms and at 3000ms and fixed nothing — the wait
     * this covers is the provider's own initialisation, which is bounded here where the scenario can
     * see it, rather than slept through where it cannot. {@link #UNAVAILABLE_DEADLINE_MS} stays
     * short so the promptness assertions still mean something.
     *
     * <p><strong>Not fully solved, and the bound is not the thing to keep raising.</strong> On a
     * loaded Docker-in-WSL host the <em>first</em> scenario of {@code errors.feature} still errors in
     * in-process mode, with the same message against the doubled 30000 ms deadline after some 53
     * seconds of wall clock. Measured three times in a row, and — importantly — it reproduces with
     * {@code @numeric-coercion} withheld exactly as it does with it declared, so it is not a
     * consequence of what this suite declares. RPC mode never shows it.
     *
     * <p>That shape is stack-side readiness rather than provider slowness: a small ruleset does not
     * take thirty seconds to sync, and only the mode that has to establish a sync stream and receive
     * the whole ruleset after the first {@code POST /start} is affected. It is the class of defect
     * open-feature/flagd-testbed#394 exists to close — a control endpoint returning before the
     * backend is serving — and the TCK's own rule applies: a suite that sleeps instead of holding the
     * control API to its promise stops being able to detect when the promise breaks. So the bound
     * stays at 15000 and this is recorded rather than covered.
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
     * <p>Everything declarable except {@link Capability#REINITIALIZATION} and
     * {@link Capability#LARGE_INTEGERS}, both of which are facts about the provider or the SDK
     * rather than defects, and both explained below.
     *
     * <p><strong>{@link Capability#NUMERIC_COERCION} is declared even though one of its scenarios
     * fails</strong>, and that is deliberate — see {@link #knownDeviations()} for the reasoning.
     * Evaluating {@code float-flag} (0.5) through the integer API returns {@code 0} with <em>no</em>
     * error code rather than {@code TYPE_MISMATCH} with the code default, so the fractional part is
     * discarded silently. Coercion as such is permitted, and the capability says so: the rule is
     * that a lossless coercion must succeed and a lossy one must fail. It is the lossy case being
     * accepted that is a defect to fix.
     *
     * <p>Measured rather than assumed, in both modes: of the tag's three scenarios, "An integer
     * requested as a float is widened without loss" <em>passes</em> — which is the fact that settles
     * the shape of the report, because a provider that performs the coercion and gets one direction
     * wrong is not a provider that declines to coerce. The lossy scenario fails, and the remaining
     * lossless one fails only because {@code integral-float-flag} is absent from the pinned testbed
     * image.
     *
     * <p>flagd's numeric behaviour is identical across both resolvers, which places the defect in
     * the shared provider layer rather than in either transport. Every other capability, including
     * the full non-numeric type-mismatch matrix, holds in both modes.
     *
     * <p>That includes {@link Capability#LIFECYCLE}, and legitimately so: flagd reaches its backend
     * during initialisation in both modes — an RPC round trip, or a full ruleset sync — so the
     * lifecycle scenarios assert something real here rather than passing vacuously. Worth stating
     * because the Go and JavaScript flagd providers withhold it; Java declaring it is what made that
     * divergence visible, and the other two are being changed to match rather than the reverse.
     *
     * <p><strong>{@link Capability#REINITIALIZATION} is withheld, and that is a fact about the
     * provider rather than a defect in it.</strong> {@code shutdown()} sets the sync resources' own
     * {@code isShutDown} flag and never clears {@code isInitialized}
     * (FlagdProvider.java:136-155, FlagdProviderSyncResources.java:27-28, 112-115), so a later
     * {@code initialize()} returns at its first check without rebuilding anything
     * (FlagdProvider.java:121-125): the resolver is shut down, the RPC channel was
     * {@code shutdownNow()}'d, the retry scheduler is terminated and {@code errorExecutor} is a
     * {@code final} field nothing re-creates. A shut-down flagd provider is terminally shut down.
     *
     * <p>Requirement 2.5.2 says a provider <em>SHOULD</em> revert to its uninitialized state after
     * shutdown, and its supporting text says <em>"some providers MAY allow reinitialization from
     * this state"</em> — so reuse is permitted, not required, and declining it is one of the options
     * the requirement offers. An earlier version of this file recorded it as a {@code KnownDeviation}
     * against {@code @lifecycle}, which was wrong twice over: the scenario was mandatory only because
     * the spec's assets had not yet gated it, and the entry asserted a defect against a provider
     * behaving within the requirement. Withholding the tag is the whole of what is owed here; the
     * one scenario it gates is reported as skipped with this reason on every run.
     *
     * <p>{@link Capability#STALE} is declared for both resolvers, and the declaration is examined
     * rather than inherited. {@code PROVIDER_STALE} is emitted from {@code FlagdProvider.onError}
     * (FlagdProvider.java:258-264), which the shared {@code onProviderEvent} switch reaches on
     * {@code PROVIDER_ERROR} from either resolver (FlagdProvider.java:197, 236), before the grace
     * period turns it into {@code PROVIDER_ERROR} — so the emit sits in the provider layer, not in a
     * transport, and the scenario "Losing the backend makes the provider stale, regaining it makes
     * it ready again" passes in RPC mode as well as in-process. Worth stating because Go's flagd
     * provider withholds the tag for its RPC resolver; on this evidence that is a difference between
     * the two implementations, not a property of the transport.
     *
     * <p>{@link Capability#LARGE_INTEGERS} is withheld, as it is by every Java provider. It asks for
     * 2^53 − 1 and {@code Client.getIntegerDetails} is a 32-bit {@code Integer} with no room for it,
     * so the limit is the SDK's rather than flagd's — Appendix F is where that is recorded, and it is
     * not a {@link dev.openfeature.contrib.tools.tck.KnownDeviation}, because flagd is not at
     * fault for a value the accessor cannot carry. Its one scenario is reported as skipped for an
     * undeclared capability, like any other.
     *
     * <p>{@link Capability#VARIANTS} and {@link Capability#TARGETING} are both declared, and both on
     * evidence rather than by inheriting the "everything except" default. flagd names the variant it
     * served in every resolution, so seven of the {@code @variants} outline's eight rows pass in both
     * modes; the eighth asks for {@code large-integer-flag}'s {@code max-int32} and is answered with
     * no variant because testbed v3.8.0 does not serve that flag at all — the same gap that fails the
     * untagged precision scenario, recorded next to the image tag in the Compose file rather than
     * here. {@code @targeting} is the newer claim and the cheaper one to check: its three scenarios
     * resolve {@code targeting-key-flag} through flagd's own rule evaluation and all three pass in
     * both modes on the image already pinned, so nothing about it needed a testbed bump.
     *
     * <p>{@link Capability#DISABLED_FLAGS} is declared, and measured rather than assumed. Both
     * resolvers substitute the caller's default for a flag whose state is {@code DISABLED} and report
     * no error code, so all four rows of that outline pass in both modes. Measured on the pinned
     * image: RPC is 56 scenarios, 50 passing, two skipped for the withheld tags above and four
     * failing — the one real defect plus the three testbed gaps already described. In-process is the
     * same four failures plus the cold-start initialisation error recorded on
     * {@link #CONNECTED_DEADLINE_MS}, so 49 passing. Nothing needed bumping for it either: {@code disabled-boolean-flag},
     * {@code disabled-string-flag}, {@code disabled-integer-flag} and {@code disabled-float-flag}
     * are flagd-testbed's own, from {@code flags/disabled-flags.json}, which the image already
     * carries at the v3.8.0 pinned below and which the launchpad combines into the set it serves. The
     * canonical definition took the testbed's names and values rather than inventing its own, exactly
     * as it did for the falsy flags.
     *
     * <p>That the capability holds here is worth stating rather than assuming, because the tag is
     * gated on architecture rather than on quality and flagd sits on the right side of that line
     * twice over: the in-process resolver evaluates the ruleset locally, and the RPC resolver still
     * decides locally what to do with a response that carries no value. A provider whose backend
     * decides — one speaking OFREP — cannot hold it at all, which is the comparison the tag exists to
     * make legible.
     *
     * <p>{@link Capability#declarableExcept} rather than {@code EnumSet.complementOf}, which is what
     * this used to be. The complement of one capability is every other <em>enum constant</em>,
     * including {@code @caching} — a reserved tag no scenario carries — so declaring the complement
     * claimed a capability nothing had examined, and the suite refuses such a declaration at startup.
     * It swept up {@code @targeting} the same way until that tag gated something, which is the point:
     * the hazard shrinks as the vocabulary fills up and never disappears, so the form of the call is
     * what protects the declaration, not the current size of the reserved set.
     */
    @Override
    public Set<Capability> capabilities() {
        return Capability.declarableExcept(Capability.REINITIALIZATION, Capability.LARGE_INTEGERS);
    }

    /**
     * {@inheritDoc}
     *
     * <p>One entry, for {@link Capability#NUMERIC_COERCION}, and it takes the <strong>declared and
     * failing</strong> shape rather than the withheld-and-skipped one. That is the shape the TCK's
     * guidance prefers, and the reason it prefers it is exactly this case: flagd <em>does</em>
     * attempt the coercion — the widening scenario passes — and gets the narrowing direction wrong,
     * so withdrawing the capability would turn a real failure into a skip, which is the failure mode
     * the field exists to prevent. The failure stays visible in the results and this entry says it is
     * known and why.
     *
     * <p>An earlier version of this file withheld the tag instead, on the argument that the
     * capability requires all three of its scenarios and one of them asks for
     * {@code integral-float-flag}, which the pinned testbed image does not serve — so declaring it
     * buys one honest failure and one that is the stack's fault. That cost is real and it is
     * accepted, for two reasons. It is not a new kind of cost: this branch already carries two
     * failures caused by the same missing flags and records them plainly rather than hiding them
     * behind a withheld capability. And the alternative is worse, because a skip cannot distinguish
     * "flagd declines to coerce" from "flagd coerces and gets it wrong", and only the second is true.
     *
     * <p>Tracked against flagd's numeric coercion ADR, which is where the rule this deviates from is
     * settled: coercion is permitted when it is lossless and must fail with {@code TYPE_MISMATCH}
     * only when information would be lost. The summary says which half is broken, because "flagd
     * coerces numbers" on its own reads as a description of intended behaviour. Delete the entry once
     * the lossy case reports {@code TYPE_MISMATCH}; the capability needs no change then, which is
     * another small argument for this shape.
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
