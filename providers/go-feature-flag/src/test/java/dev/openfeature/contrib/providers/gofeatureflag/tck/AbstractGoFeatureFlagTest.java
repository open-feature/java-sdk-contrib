package dev.openfeature.contrib.providers.gofeatureflag.tck;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProvider;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.tools.tck.BackendEndpoint;
import dev.openfeature.contrib.tools.tck.ContainerizedProviderTckTest;
import dev.openfeature.sdk.FeatureProvider;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Shared configuration for running the OpenFeature Provider TCK against the GO Feature Flag
 * provider.
 *
 * <p>The provider evaluates in two quite different ways, and both are worth conforming: in-process
 * pulls the flag configuration from the relay proxy and evaluates it in a WASM build of the GO
 * Feature Flag engine, while remote asks the relay proxy over OFREP. They share a backend and differ
 * only in evaluation type, so the modes are two small subclasses.
 *
 * <p>The backend is <a href="https://github.com/aepfli/goff-tck-testbed">aepfli/goff-tck-testbed</a>:
 * the unmodified relay proxy, with a launchpad implementing the control API that writes its flag
 * file and forces a retriever refresh.
 */
abstract class AbstractGoFeatureFlagTest extends ContainerizedProviderTckTest {

    /** The relay proxy's container-internal port, serving both evaluation types. */
    private static final int RELAY_PROXY_PORT = 1031;

    /**
     * A port nothing listens on, for the initialisation-failure scenarios.
     *
     * <p>Deliberately not a port on the Compose stack: the stack must stay up for the whole suite,
     * and simulated outages belong to the control API.
     */
    private static final int UNAVAILABLE_PORT = 9999;

    /**
     * How often the in-process evaluator polls the relay proxy for a changed configuration.
     *
     * <p>Lowered from the two-minute default, against which no event scenario can complete. Remote
     * evaluation ignores it.
     */
    private static final long FLAG_CHANGE_POLLING_INTERVAL_MS = 500;

    /** The evaluation type under test. */
    protected abstract EvaluationType evaluationType();

    @Override
    public File composeFile() {
        return new File("src/test/resources/tck/docker-compose.yaml");
    }

    @Override
    public List<Integer> backendPorts() {
        return Collections.singletonList(RELAY_PROXY_PORT);
    }

    @Override
    public FeatureProvider createProvider(BackendEndpoint endpoint) {
        return provider(String.format("http://%s:%d/", endpoint.host(), endpoint.port(RELAY_PROXY_PORT)));
    }

    @Override
    public FeatureProvider createUnavailableProvider() {
        return provider(String.format("http://localhost:%d/", UNAVAILABLE_PORT));
    }

    private FeatureProvider provider(String endpoint) {
        try {
            return new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .endpoint(endpoint)
                    .evaluationType(evaluationType())
                    .flagChangePollingIntervalMs(FLAG_CHANGE_POLLING_INTERVAL_MS)
                    // The data collector is not part of the provider contract, and its background
                    // flushes would only add traffic to the backend under test.
                    .disableDataCollection(true)
                    .build());
        } catch (InvalidOptions e) {
            throw new IllegalStateException("invalid GO Feature Flag provider options", e);
        }
    }
}
