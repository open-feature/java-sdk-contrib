package dev.openfeature.contrib.providers.ofrep.e2e;

import dev.openfeature.contrib.providers.ofrep.OfrepProvider;
import dev.openfeature.contrib.providers.ofrep.OfrepProviderOptions;
import dev.openfeature.contrib.tools.providertck.BackendEndpoint;
import dev.openfeature.contrib.tools.providertck.Capability;
import dev.openfeature.contrib.tools.providertck.ContainerizedProviderTckTest;
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
 * speaks it. flagd does, on port {@value #OFREP_PORT}, which means this suite reuses the flagd
 * testbed image and its launchpad control API unchanged — see
 * {@code src/test/resources/tck/docker-compose.yaml}.
 *
 * <p><strong>The testbed does not yet serve the whole canonical flag set.</strong> Three flags the
 * suite's assets added are absent from {@code flagd-testbed} v3.8.0: {@code large-integer-flag},
 * {@code huge-integer-flag} and {@code integral-float-flag}. Only the first is reached —
 * {@code huge-integer-flag} is asked for solely under {@code @large-integers}, which is not
 * applicable in Java, and {@code integral-float-flag} solely under {@code @numeric-coercion}, which
 * is withheld below — so exactly one untagged scenario, the 32-bit precision one, fails with
 * {@code FLAG_NOT_FOUND} until open-feature/flagd-testbed#392 lands.
 *
 * <p>The three falsy flags used to fail the same way and no longer do. The testbed's
 * {@code zero-flags.json} already served {@code boolean-zero-flag}, {@code integer-zero-flag} and
 * {@code string-zero-flag} with {@code zero}/{@code non-zero} variants, while the canonical set
 * called them {@code false-flag}, {@code zero-flag} and {@code empty-string-flag}; spec ba002ce8
 * renamed the canonical flags to the testbed's names rather than the other way round.
 *
 * <p>A missing flag is a gap in the stack, not in the provider, so it is recorded here rather than
 * declared as a {@code KnownDeviation}: a deviation says the provider is wrong, and the provider was
 * never given the flag to get wrong.
 */
public class OfrepTckTest extends ContainerizedProviderTckTest {

    /** The container-internal port flagd serves the OFREP HTTP API on. */
    private static final int OFREP_PORT = 8016;

    /**
     * A port nothing listens on, for the initialisation-failure scenarios.
     *
     * <p>Deliberately not a port on the Compose stack: the stack must stay up for the whole suite,
     * and simulated outages belong to the control API.
     */
    private static final int UNAVAILABLE_PORT = 9999;

    @Override
    public File composeFile() {
        return new File("src/test/resources/tck/docker-compose.yaml");
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
     * reported <em>promptly</em>. They are skipped for this provider — see
     * {@link #capabilities()} — but the deadlines stay correct so that the scenarios start passing
     * on their own the day the provider grows an {@code initialize()}.
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
     * (Resolver.java:54-97, OfrepApi.java:93-118). There is nothing in it that could observe a
     * backend transition, let alone report one.
     *
     * <ul>
     *   <li><b>{@link Capability#LIFECYCLE}</b> — there is no initialisation to observe.
     *       {@code constructProvider} only validates its arguments and never touches the network
     *       (OfrepProvider.java:38-68), and the interface default {@code initialize()} does
     *       nothing, so the {@code PROVIDER_READY} a client sees is the SDK's, and the readiness
     *       scenario would pass exactly as it does for {@code NoOpProvider}. The tag gates the
     *       shutdown scenarios too — shutting down twice, and shutting down promptly against a dead
     *       backend — and they are skipped rather than passed vacuously.
     *   <li><b>{@link Capability#REINITIALIZATION}</b> — omitted, and independently of the omission
     *       above. {@code shutdown()} terminates the executor the HTTP client runs on
     *       (OfrepProvider.java:90-108) and, with no {@code initialize()} of its own, nothing ever
     *       recreates it, so a shut-down {@code OfrepProvider} cannot be started again. Requirement
     *       2.5.2 permits exactly that — a provider <em>SHOULD</em> revert to its uninitialized
     *       state and <em>"some providers MAY allow reinitialization from this state"</em> — so this
     *       is a choice the specification offers and not a defect to declare. It is named here
     *       rather than left to the {@code @lifecycle} skip because
     *       {@link Capability#declarableExcept} would otherwise have claimed it, and a claim nothing
     *       examined is exactly what the declaration exists to prevent.
     *   <li><b>{@link Capability#EVENTS}</b> — the class declares {@code implements FeatureProvider},
     *       not {@code extends EventProvider} (OfrepProvider.java:19), so it has no {@code emit*}
     *       method available and calls none. The whole file contains no reference to
     *       {@code ProviderEvent}. Declaring the capability would be claiming behaviour the
     *       provider does not have — and would silently assert an untestable
     *       {@code PROVIDER_ERROR}.
     *   <li><b>{@link Capability#STALE}</b> — requires noticing that the backend went away between
     *       evaluations. Nothing survives a call: {@code Resolver.resolve} builds its result purely
     *       from the current response and, on {@code IOException}, returns
     *       {@code ErrorCode.GENERAL} without recording anything (Resolver.java:93-96,
     *       OfrepApi.java:114-115). No state, no transition, no {@code PROVIDER_STALE}.
     *   <li><b>{@link Capability#CONFIGURATION_CHANGE}</b> — needs a subscription to the backend.
     *       The only outbound call in the provider is the per-evaluation
     *       {@code POST /ofrep/v1/evaluate/flags/{key}} (OfrepApi.java:27, 93-109). There is no
     *       bulk endpoint, no ETag handling and no watch, so a change is never detected as an
     *       event — merely reflected by the next evaluation.
     *   <li><b>{@link Capability#UNAVAILABLE_INIT}</b> — with no {@code initialize()} of its own,
     *       initialisation cannot fail. A provider pointed at a dead port therefore reaches
     *       {@code READY}, which is the opposite of what the scenario asserts.
     * </ul>
     *
     * <p><b>{@link Capability#NUMERIC_COERCION}</b> is withheld for a different reason: the
     * provider keeps the two numeric types strictly apart, in both directions. Deserialisation goes
     * through a plain Jackson {@code ObjectMapper} into an untyped {@code Object value}
     * (OfrepResponse.java:16, OfrepApi.java:109), which maps a JSON integer to {@link Integer} and
     * a JSON fraction to {@link Double}, and {@code handleResolved} then admits the value only on
     * an exact {@code type.isInstance(responseValue)} check, otherwise returning
     * {@code TYPE_MISMATCH} with the code default (Resolver.java:183-191). There is no integral
     * check and no round trip anywhere in that path — nothing in the provider widens or narrows a
     * number.
     *
     * <p>The tag's rule is that lossless coercion must succeed and lossy coercion must return
     * {@code TYPE_MISMATCH}, and the three scenarios carrying it test all three cases; a provider
     * declaring the tag must pass all three. This provider passes one. The lossy case is right for
     * the wrong reason — {@code float-flag} (0.5) requested as an integer is rejected rather than
     * truncated to {@code 0}, because it is a {@link Double} and not because 0.5 is fractional —
     * and both lossless cases fail on the same exact-instance check. {@code integer-flag} (10)
     * requested as a float arrives as an {@code Integer}, which {@code Double.class.isInstance}
     * rejects, so "an integer requested as a float is widened without loss" cannot pass;
     * {@code integral-float-flag} (10.0) requested as an integer arrives as a {@link Double},
     * which {@code Integer.class.isInstance} rejects, so "an integral float requested as an
     * integer is coerced without loss" cannot pass either. Declaring the tag would turn two of its
     * three scenarios red.
     *
     * <p>No {@code KnownDeviation} accompanies it, and that is deliberate rather than silence.
     * Appendix F is explicit that {@code @numeric-coercion} is the one capability the specification
     * does not define: OpenFeature has a single {@code number} type, the rule this tag is tested
     * against is borrowed from flagd's numeric-coercion ADR, and <em>"a provider that behaves
     * differently is not violating the specification"</em>. The appendix says so having retracted
     * an earlier draft that called non-declaration "an admission of a known bug". Strict typing in
     * both directions is therefore a legitimate choice — the SDK's own {@code InMemoryProvider}
     * makes it — and a deviation entry would assert a defect the spec says is not one. That is the
     * opposite mistake from a vacuous declaration, but a mistake in the same currency.
     *
     * <p>Go's OFREP provider declares the tag, and that is not an inconsistency to reconcile away:
     * it coerces through an integral check and this one does not, so the two declarations describe
     * two implementations rather than one protocol. OFREP being JSON is what makes the difference
     * possible — one wire number type, so integer-ness is the provider's decision, not the
     * payload's. Declaring the tag here to match Go would also run the
     * {@code integral-float-flag} scenario, which the testbed cannot serve, so it would fail twice
     * over: once for the provider and once for the stack.
     *
     * <p>All of that is read from the source, because a withheld tag means the scenarios are
     * skipped and a run cannot confirm it; the three are reported as skipped with this reason on
     * every run, which is the observable that the declaration is being honoured rather than the
     * behaviour behind it. {@code OfrepProviderTest} asserts the exact-instance check itself, but
     * only across Boolean and String (OfrepProviderTest.java:71, 338-339) — no unit test pins the
     * numeric pair, which is why the reasoning above cites {@code handleResolved} directly. A run in
     * which either lossless scenario passes means {@code handleResolved} or the deserialiser
     * changed, and this declaration should follow it.
     *
     * <p>Two things this leaves in place. {@link Capability#OBJECT} is declared: the same
     * exact-instance check is what makes the {@code @object} mismatch matrix work, and the
     * structured happy path passes through {@code resolve(Object.class, ...)}, which every non-null
     * value satisfies, and is converted with {@code Value.objectToValue} (Resolver.java:125-136).
     * And {@link Capability#LARGE_INTEGERS} is withheld, as every Java provider withholds it, for a
     * reason that is not about OFREP at all: the tag asks for 2^53 − 1 and
     * {@code Client.getIntegerDetails} is a 32-bit {@code Integer} with no room for it. The limit is
     * the SDK's, it is recorded once in Appendix F rather than in each run, and the scenario is
     * skipped for an undeclared capability like any other. It is named here for the same reason
     * {@code REINITIALIZATION} is — {@link Capability#declarableExcept} would otherwise claim it.
     *
     * <p>{@link Capability#VARIANTS} and {@link Capability#TARGETING} are declared, and unlike the
     * withheld tags above both are confirmed by a run rather than read from the source. The OFREP
     * response carries {@code variant} alongside {@code value} and {@code reason}, and the provider
     * passes it through, so seven of the {@code @variants} outline's eight rows pass; the eighth asks
     * for {@code large-integer-flag}'s {@code max-int32} and gets no variant because testbed v3.8.0
     * does not serve that flag — the gap already noted next to the image tag, not a second defect.
     * {@code @targeting} matters more here than the capability's name suggests: the provider sends the
     * evaluation context in the request body and the backend evaluates the rule, so the three
     * {@code targeting-key-flag} scenarios are the only ones in the canonical set that would notice a
     * context dropped on the way out. All three pass.
     *
     * <p>{@code declarableExcept} rather than {@code EnumSet.complementOf}, which would also claim
     * {@code @caching} on the way past; the suite refuses such a declaration at startup. It claimed
     * {@code @targeting} the same way until that tag gated something — the reserved set shrinks as
     * the vocabulary fills up, which is an argument for the form of the call rather than against it.
     */
    @Override
    public Set<Capability> capabilities() {
        return Capability.declarableExcept(
                Capability.LIFECYCLE,
                Capability.REINITIALIZATION,
                Capability.EVENTS,
                Capability.STALE,
                Capability.CONFIGURATION_CHANGE,
                Capability.UNAVAILABLE_INIT,
                Capability.NUMERIC_COERCION,
                Capability.LARGE_INTEGERS);
    }
}
