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

/** Runs the OpenFeature Provider TCK against the OFREP provider, backed by flagd's OFREP endpoint. */
public class OfrepTest extends ContainerizedProviderTckTest {

    private static final int OFREP_PORT = 8016;

    /** Nothing listens here; outages on the Compose stack belong to the control API. */
    private static final int UNAVAILABLE_PORT = 9999;

    /** The flagd-testbed's own stack, so this suite and the flagd adoption share one image pin. */
    @Override
    public File composeFile() {
        return new File("../flagd/test-harness/docker-compose.yaml");
    }

    @Override
    public String backendService() {
        return "flagd";
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
     * <p>The provider has no lifecycle of its own: no {@code initialize()}, no events, no state
     * between calls, and an executor {@code shutdown()} never recreates. That withholds lifecycle,
     * reinitialization, events, stale, configuration change and unavailable init. Numeric coercion
     * is withheld because the provider keeps integers and doubles strictly apart, which the rule
     * allows. Disabled flags is a defect; see {@link #knownDeviations()}.
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

    @Override
    public List<KnownDeviation> knownDeviations() {
        return Collections.singletonList(KnownDeviation.untracked(
                Capability.DISABLED_FLAGS,
                "A value-less OFREP evaluation success (codeDefaultFlag) is reported as FLAG_NOT_FOUND "
                        + "instead of the code default with the server's reason; flagd answers a "
                        + "disabled flag that way."));
    }
}
