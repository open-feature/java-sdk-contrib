package dev.openfeature.contrib.providers.ofrep.tck;

import dev.openfeature.contrib.providers.ofrep.OfrepProvider;
import dev.openfeature.contrib.providers.ofrep.OfrepProviderOptions;
import dev.openfeature.contrib.tools.tck.BackendEndpoint;
import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.ContainerizedProviderTckTest;
import dev.openfeature.contrib.tools.tck.KnownDeviation;
import dev.openfeature.sdk.FeatureProvider;
import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Runs the OpenFeature Provider TCK against the OFREP provider.
 *
 * <p>OFREP is a protocol rather than a vendor, so the backend under test is simply something that
 * speaks it. flagd does, on port {@value #OFREP_PORT}, so this suite reuses the flagd testbed image
 * and its launchpad control API unchanged, from the same Compose file as the flagd adoption.
 *
 * <p>A clean run is 65 scenarios, 46 passing, 17 skipped and 2 failing. Both failures are on a flag
 * the pinned testbed image does not serve (open-feature/flagd-testbed#392) rather than in the
 * provider, so neither is a {@link KnownDeviation}.
 *
 * <p>Roughly half of the runs measured carry one or two <em>additional</em> failures, moving between
 * {@code errors.feature}, {@code evaluation.feature} and {@code reason.feature} from run to run, so
 * the flakiness is not a property of any assertion. It is the readiness window
 * open-feature/flagd-testbed#394 exists to close, reaching a provider that holds nothing between
 * calls, so every evaluation races the stack afresh. <strong>Do not add a settle after control calls
 * to cover it</strong> — that issue says why.
 */
public class OfrepTest extends ContainerizedProviderTckTest {

    /** The container-internal port flagd serves the OFREP HTTP API on. */
    private static final int OFREP_PORT = 8016;

    /**
     * A port nothing listens on, for the initialisation-failure scenarios.
     *
     * <p>Deliberately not a port on the Compose stack: the stack must stay up for the whole suite,
     * and simulated outages belong to the control API.
     */
    private static final int UNAVAILABLE_PORT = 9999;

    /**
     * {@inheritDoc}
     *
     * <p>Outside this module on purpose: the flagd adoption runs against the same stack and names the
     * same file, so there is one image tag for both rather than two that can drift.
     */
    @Override
    public File composeFile() {
        return new File("../../tools/flagd-testbed/docker-compose.yaml");
    }

    @Override
    public List<Integer> backendPorts() {
        return Collections.singletonList(OFREP_PORT);
    }

    @Override
    public FeatureProvider createProvider(BackendEndpoint endpoint) {
        return OfrepProvider.constructProvider(OfrepProviderOptions.builder()
                .baseUrl("http://" + endpoint.host() + ":" + endpoint.port(OFREP_PORT))
                .build());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Short timeouts on purpose: the {@code @unavailable} scenarios assert that failure is
     * reported <em>promptly</em>. They are skipped for this provider — see {@link #capabilities()} —
     * but the deadlines stay correct so that the scenarios start passing on their own the day the
     * provider grows an {@code initialize()}.
     */
    @Override
    public FeatureProvider createUnavailableProvider() {
        return OfrepProvider.constructProvider(OfrepProviderOptions.builder()
                .baseUrl("http://localhost:" + UNAVAILABLE_PORT)
                .connectTimeout(Duration.ofMillis(500))
                .requestTimeout(Duration.ofMillis(500))
                .build());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Six capabilities are withheld for the same root cause: {@code OfrepProvider} is a bare
     * {@link dev.openfeature.sdk.FeatureProvider} (OfrepProvider.java:19) with no lifecycle of its
     * own. It holds no state, opens no stream, runs no poll loop and does not override
     * {@code initialize()} — every evaluation is a fresh, independent HTTP POST
     * (Resolver.java:54-97, OfrepApi.java:93-118).
     *
     * <ul>
     *   <li><b>{@link Capability#LIFECYCLE}</b> — {@code constructProvider} only validates its
     *       arguments and never touches the network (OfrepProvider.java:38-68), so the
     *       {@code PROVIDER_READY} a client sees is the SDK's and the readiness scenario would pass
     *       as vacuously as it does for {@code NoOpProvider}. The tag gates the shutdown scenarios
     *       too, and they are skipped rather than passed vacuously.
     *   <li><b>{@link Capability#REINITIALIZATION}</b> — {@code shutdown()} terminates the executor
     *       the HTTP client runs on (OfrepProvider.java:90-108) and nothing ever recreates it, so a
     *       shut-down provider cannot be started again. Named here rather than left to the
     *       {@code @lifecycle} skip because {@code declarableExcept} would otherwise claim it.
     *   <li><b>{@link Capability#EVENTS}</b> — the class implements {@code FeatureProvider} rather
     *       than extending {@code EventProvider} (OfrepProvider.java:19), so it has no {@code emit*}
     *       method available and the file contains no reference to {@code ProviderEvent}.
     *   <li><b>{@link Capability#STALE}</b> — nothing survives a call: {@code Resolver.resolve}
     *       builds its result purely from the current response and, on {@code IOException}, returns
     *       {@code ErrorCode.GENERAL} without recording anything (Resolver.java:93-96,
     *       OfrepApi.java:114-115). No state, no transition, no {@code PROVIDER_STALE}.
     *   <li><b>{@link Capability#CONFIGURATION_CHANGE}</b> — the only outbound call is the
     *       per-evaluation {@code POST /ofrep/v1/evaluate/flags/{key}} (OfrepApi.java:27, 93-109).
     *       No bulk endpoint, no ETag handling, no watch, so a change is never detected as an event.
     *   <li><b>{@link Capability#UNAVAILABLE_INIT}</b> — with no {@code initialize()} of its own,
     *       initialisation cannot fail; a provider pointed at a dead port reaches {@code READY},
     *       which is the opposite of what the scenario asserts.
     * </ul>
     *
     * <p><b>{@link Capability#NUMERIC_COERCION}</b> is withheld for a different reason: the provider
     * keeps the two numeric types strictly apart, in both directions. Jackson maps a JSON integer to
     * {@link Integer} and a JSON fraction to {@link Double} (OfrepResponse.java:16,
     * OfrepApi.java:109), and {@code handleResolved} then admits the value only on an exact
     * {@code type.isInstance(responseValue)} check (Resolver.java:183-191) — nothing in the provider
     * widens or narrows a number. Both lossless scenarios would fail on that check, so declaring the
     * tag would turn two of its three scenarios red. No {@link KnownDeviation} accompanies it,
     * deliberately: {@link Capability#NUMERIC_COERCION} records that the rule is borrowed rather than
     * normative, so strict typing in both directions is a legitimate choice — the SDK's own
     * {@code InMemoryProvider} makes it.
     *
     * <p>That is read from the source, because a withheld tag means the scenarios are skipped and a
     * run cannot confirm it; no unit test pins the numeric pair either. A run in which either
     * lossless scenario passes means {@code handleResolved} or the deserialiser changed, and this
     * declaration should follow it.
     *
     * <p><strong>{@link Capability#STRING_TYPING} and {@link Capability#FULLY_TYPED_VALUES} are both
     * declared, and all four of their scenarios pass.</strong> The mechanism is the same
     * exact-instance check reached from the other side: {@code String.class.isInstance} of a
     * {@link Boolean}, an {@link Integer} or a {@link Double} is false, so {@code handleResolved}
     * returns {@code TYPE_MISMATCH} with the code default rather than the value's {@code toString()}.
     * One line of code answers all four, including the float and the structure that the second tag
     * asks about separately, so this provider is on the typed side of both.
     *
     * <p>{@link Capability#OBJECT} is declared: the structured happy path passes through
     * {@code resolve(Object.class, ...)}, which every non-null value satisfies, and is converted with
     * {@code Value.objectToValue} (Resolver.java:125-136). {@link Capability#LARGE_INTEGERS} is
     * absent below for a reason that says nothing about OFREP — the TCK refuses it centrally.
     *
     * <p>{@link Capability#VARIANTS} and {@link Capability#TARGETING} are declared, and unlike the
     * withheld tags both are confirmed by a run. The OFREP response carries {@code variant} and the
     * provider passes it through, so seven of the {@code @variants} outline's eight rows pass and the
     * eighth is the testbed gap above. {@code @targeting} matters more here than its name suggests:
     * the provider sends the evaluation context in the request body, so its three scenarios are the
     * only ones in the canonical set that would notice a context dropped on the way out. All three
     * pass.
     *
     * <p>{@link Capability#STANDARD_REASONS} arrived by the {@code declarableExcept} default rather
     * than by a decision, so it was measured: eight of {@code reason.feature}'s nine scenarios run
     * and pass. The ninth carries {@code @disabled-flags} and is skipped for that omission, so the tag
     * here means the standard vocabulary over the responses this provider completes; a consumer sees
     * the withheld {@code @disabled-flags} beside it and can tell which scenario went unasked.
     *
     * <p><b>{@link Capability#DISABLED_FLAGS}</b> is withheld, and unlike every other withheld tag it
     * was <em>measured</em>: declared, all four rows of its outline fail, and on the error code rather
     * than on the value. flagd's OFREP endpoint does not 404 a disabled flag — it answers {@code 200}
     * with a {@code reason} of {@code DISABLED} and <em>no</em> {@code value} member, which is the
     * protocol's {@code codeDefaultFlag} shape and means the provider must use the code default.
     * {@code handleResolved} reaches its {@code responseValue == null} branch and returns
     * {@code FLAG_NOT_FOUND} instead, discarding the {@code reason} it parsed one field earlier
     * (Resolver.java:169-181, OfrepResponse.java:19). So this is a provider gap rather than an
     * architectural limit, and wider than the four rows that found it — hence a
     * {@link KnownDeviation}, unlike {@code @numeric-coercion} above, because {@code codeDefaultFlag}
     * is a {@code MUST} in the protocol this provider implements. Delete both once
     * {@code handleResolved} honours a value-less success.
     *
     * <p>{@code declarableExcept} and not {@code EnumSet.complementOf}, whose complement would claim
     * reserved tags the suite refuses at startup.
     */
    @Override
    public Set<Capability> capabilities() {
        return Capability.declarableExcept(
                Capability.LIFECYCLE,
                Capability.REINITIALIZATION,
                Capability.EVENTS,
                Capability.STALE,
                Capability.CONFIGURATION_CHANGE,
                Capability.DISABLED_FLAGS,
                Capability.UNAVAILABLE_INIT,
                Capability.NUMERIC_COERCION);
    }

    /**
     * {@inheritDoc}
     *
     * <p>One entry, for the withheld {@link Capability#DISABLED_FLAGS}, and the only omission in
     * {@link #capabilities()} that is a defect rather than a fact about the provider's shape.
     * Untracked, because there is no issue to point at yet. The summary names the response shape
     * rather than the scenario: {@code codeDefaultFlag} is not specific to disabled flags, so a reader
     * comparing providers needs the general statement and not the one outline that caught it.
     */
    @Override
    public List<KnownDeviation> knownDeviations() {
        return Collections.singletonList(KnownDeviation.untracked(
                Capability.DISABLED_FLAGS,
                "A value-less OFREP evaluation success is reported to the application as "
                        + "FLAG_NOT_FOUND. OFREP's evaluationSuccess requires only key and reason; a "
                        + "success matching codeDefaultFlag carries no value and means the provider "
                        + "MUST use the code default. handleResolved treats a null value as an "
                        + "absent flag instead (Resolver.java:174-181), discarding the reason it "
                        + "parsed, so the value returned is right and the error code is not. Found "
                        + "by the four @disabled-flags rows -- flagd answers a DISABLED flag with "
                        + "200, reason DISABLED and no value -- but it affects every codeDefaultFlag "
                        + "response, not only disabled flags."));
    }
}
