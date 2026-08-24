package dev.openfeature.contrib.providers.ofrep.e2e;

import dev.openfeature.contrib.providers.ofrep.OfrepProvider;
import dev.openfeature.contrib.providers.ofrep.OfrepProviderOptions;
import dev.openfeature.contrib.tools.providertck.AbstractProviderTckTest;
import dev.openfeature.contrib.tools.providertck.BackendEndpoint;
import dev.openfeature.contrib.tools.providertck.Capability;
import dev.openfeature.sdk.FeatureProvider;
import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Runs the OpenFeature Provider TCK against the OFREP provider.
 *
 * <p>OFREP is a protocol rather than a vendor, so the backend under test is simply something that
 * speaks it. flagd does, on port {@value #OFREP_PORT}, which means this suite reuses the flagd
 * testbed image and its launchpad control API unchanged — see
 * {@code src/test/resources/tck/docker-compose.yaml}.
 */
public class OfrepTckTest extends AbstractProviderTckTest {

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
     * <p>Four capabilities are withheld, and all four come from the same root cause: {@code
     * OfrepProvider} is a bare {@link dev.openfeature.sdk.FeatureProvider} (OfrepProvider.java:19)
     * with no lifecycle of its own. It holds no state, opens no stream, runs no poll loop and does
     * not override {@code initialize()} — every evaluation is a fresh, independent HTTP POST
     * (Resolver.java:54-97, OfrepApi.java:93-118). There is nothing in it that could observe a
     * backend transition, let alone report one.
     *
     * <ul>
     *   <li><b>{@link Capability#EVENTS}</b> — the class declares {@code implements FeatureProvider},
     *       not {@code extends EventProvider} (OfrepProvider.java:19), so it has no {@code emit*}
     *       method available and calls none. The whole file contains no reference to
     *       {@code ProviderEvent}. The {@code PROVIDER_READY} that a client does observe is
     *       synthesised by the SDK on successful initialisation and would appear for
     *       {@code NoOpProvider} just the same; it is not the provider participating in the event
     *       system, so declaring the capability would be claiming behaviour the provider does not
     *       have — and would silently assert an untestable {@code PROVIDER_ERROR}.
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
     *   <li><b>{@link Capability#UNAVAILABLE_INIT}</b> — {@code OfrepProvider} does not override
     *       {@code initialize(EvaluationContext)}, so the interface default runs and initialisation
     *       cannot fail. {@code constructProvider} only validates its arguments; it never touches
     *       the network (OfrepProvider.java:38-68). A provider pointed at a dead port therefore
     *       reaches {@code READY}, which is the opposite of what the scenario asserts.
     * </ul>
     *
     * <p>{@link Capability#OBJECT} and {@link Capability#STRICT_NUMERIC_TYPING} are both declared,
     * and the second is worth spelling out because the flagd provider cannot declare it.
     * Deserialisation goes through a plain Jackson {@code ObjectMapper} into an untyped
     * {@code Object value} (OfrepResponse.java:16, OfrepApi.java:109), which maps a JSON integer to
     * {@link Integer} and a JSON fraction to {@link Double}. {@code handleResolved} then admits the
     * value only on an exact {@code type.isInstance(responseValue)} check and otherwise returns
     * {@code TYPE_MISMATCH} with the code default (Resolver.java:183-190). Nothing anywhere widens
     * or narrows between the two numeric types, so {@code float-flag} (0.5) requested as an integer
     * is rejected rather than truncated to {@code 0}. The same exact-instance check is what makes
     * the {@code @object} mismatch matrix work; the structured happy path passes through
     * {@code resolve(Object.class, ...)}, which every non-null value satisfies, and is converted
     * with {@code Value.objectToValue} (Resolver.java:125-136).
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.complementOf(EnumSet.of(
                Capability.EVENTS, Capability.STALE, Capability.CONFIGURATION_CHANGE, Capability.UNAVAILABLE_INIT));
    }
}
