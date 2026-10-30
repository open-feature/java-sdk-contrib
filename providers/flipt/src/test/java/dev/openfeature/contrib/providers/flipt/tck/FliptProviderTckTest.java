package dev.openfeature.contrib.providers.flipt.tck;

import dev.openfeature.contrib.providers.flipt.FliptProvider;
import dev.openfeature.contrib.providers.flipt.FliptProviderConfig;
import dev.openfeature.contrib.tools.tck.BackendEndpoint;
import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.ContainerizedProviderTckTest;
import dev.openfeature.sdk.FeatureProvider;
import io.flipt.api.FliptClient;
import io.flipt.api.FliptClient.FliptClientBuilder;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * The OpenFeature Provider Conformance Suite against the Flipt <b>Java</b> provider.
 *
 * <p>The backend is the shared Flipt provider-TCK testbed published from
 * <a href="https://github.com/flipt-io/openfeature-tck">flipt-io/openfeature-tck</a>: Flipt plus a
 * control server that implements the standard control API.
 *
 * <p>Capabilities are declared through {@link Capability#declarableExcept} so that the reserved and
 * inexpressible entries are never swept in by accident. {@code OBJECT}, {@code TARGETING},
 * {@code DISABLED_FLAGS} and {@code NUMERIC_COERCION} are declared; the rest are withheld for the
 * reasons below.
 *
 * <p>{@code LIFECYCLE} and {@code UNAVAILABLE_INIT} are withheld because {@code initialize} builds
 * the client but does not reach the backend. {@code REINITIALIZATION} is withheld because the
 * provider keeps its initialized flag across shutdown. {@code EVENTS}, {@code STALE} and
 * {@code CONFIGURATION_CHANGE} are withheld because the provider emits no backend events.
 *
 * <p>{@code VARIANTS} is withheld because Flipt's boolean evaluation carries no variant. {@code
 * STRING_TYPING} and {@code FULLY_TYPED_VALUES} are withheld because Flipt stores scalar values as
 * variant-key text, so a typed store cannot report the mismatches those scenarios ask for.
 *
 * <p>{@code STANDARD_REASONS} is withheld because Flipt answers the same
 * {@code DEFAULT_EVALUATION_REASON} for a rule-less flag and for a targeting rule that matched
 * nothing, so the provider cannot distinguish {@code STATIC} from {@code DEFAULT}. The vendor
 * behavior is covered by {@code extensions/flipt-reason.feature} instead.
 *
 * <p>{@code LARGE_INTEGERS} is not declared: Java's integer accessor is a 32-bit {@code Integer},
 * so the suite refuses it automatically.
 */
public class FliptProviderTckTest extends ContainerizedProviderTckTest {

    /** Flipt's evaluation API inside the testbed container. */
    private static final int FLIPT_PORT = 8888;

    @Override
    public File composeFile() {
        // Resolved relative to this Maven module (providers/flipt).
        return new File("src/test/resources/tck/docker-compose.yaml");
    }

    @Override
    public List<Integer> backendPorts() {
        return List.of(FLIPT_PORT);
    }

    @Override
    public FeatureProvider createProvider(BackendEndpoint endpoint) {
        String baseUrl = String.format("http://%s:%d", endpoint.host(), endpoint.port(FLIPT_PORT));
        return providerAt(baseUrl, Duration.ofSeconds(5));
    }

    @Override
    public FeatureProvider createUnavailableProvider() {
        // A closed port on localhost, never the backend under test: the stack has to stay up, and
        // simulated outages belong to the control API. LIFECYCLE and UNAVAILABLE_INIT are withheld,
        // so this factory exists only to satisfy the containerized contract.
        return providerAt("http://localhost:9999", Duration.ofMillis(500));
    }

    @Override
    public Set<Capability> capabilities() {
        return Capability.declarableExcept(
                Capability.LIFECYCLE,
                Capability.REINITIALIZATION,
                Capability.EVENTS,
                Capability.STALE,
                Capability.CONFIGURATION_CHANGE,
                Capability.VARIANTS,
                Capability.STRING_TYPING,
                Capability.FULLY_TYPED_VALUES,
                Capability.UNAVAILABLE_INIT,
                Capability.STANDARD_REASONS);
    }

    @Override
    public String configuration() {
        return "flipt";
    }

    @Override
    public Duration startupTimeout() {
        // The first run pulls the testbed image and starts Flipt; 60 seconds is tight for that.
        return Duration.ofMinutes(3);
    }

    private static FeatureProvider providerAt(String baseUrl, Duration timeout) {
        FliptClientBuilder clientBuilder = FliptClient.builder().url(baseUrl).timeout(timeout);
        FliptProviderConfig config =
                FliptProviderConfig.builder().fliptClientBuilder(clientBuilder).build();
        return new FliptProvider(config);
    }
}
