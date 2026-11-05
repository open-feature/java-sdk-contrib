package openfeature.tck.extensions;

import dev.openfeature.contrib.providers.flagd.tck.FlagdTckHarness;
import dev.openfeature.contrib.tools.tck.BackendEndpoint;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import io.cucumber.java.en.Given;

/**
 * Steps for the connection extension scenarios: pointing the provider at the envoy sidecar.
 *
 * <p>Event ordering used to live here too, because the TCK's await discarded the records ahead of
 * its match and an {@code error} recorded before an awaited {@code ready} was gone by the time
 * anything could object to it. The TCK keeps its own records now and asks the question itself, with
 * {@code the {} event handler should have been executed with no {} event before it}.
 */
public class ConnectionSteps {

    /** A port nothing listens on, so a provider given a target URI cannot fall back to host and port. */
    private static final int UNAVAILABLE_PORT = 9999;

    private final FlagdTckHarness flagd;

    public ConnectionSteps(ProviderTckHarness harness) {
        this.flagd = (FlagdTckHarness) harness;
    }

    /**
     * Connects the next provider through envoy, by a target URI whose {@code <port>} is envoy's
     * routing listener. The host and port options point at a closed port, as the testbed does, so
     * the scenario passes only if the target URI is what the provider used.
     *
     * @param targetUri the testbed's target URI, with {@code localhost:<port>} standing for envoy
     */
    @Given("a target URI {string} routed through envoy")
    public void aTargetUriRoutedThroughEnvoy(String targetUri) {
        BackendEndpoint endpoint = flagd.backendEndpoint();
        String resolved = targetUri
                .replace("localhost", endpoint.host(FlagdTckHarness.ENVOY_SERVICE))
                .replace(
                        "<port>",
                        String.valueOf(
                                endpoint.port(FlagdTckHarness.ENVOY_SERVICE, FlagdTckHarness.ENVOY_ROUTING_PORT)));
        flagd.customize(builder -> builder.targetUri(resolved).port(UNAVAILABLE_PORT));
    }

    /**
     * Points the next provider at envoy's listener that answers every call with HTTP 403, which
     * gRPC reports as {@code PERMISSION_DENIED}.
     *
     * <p>Followed by the TCK's {@code Given a unavailable provider}, the flavour that does not wait
     * for readiness; a forbidden provider never becomes ready.
     */
    @Given("the provider connects to envoy's forbidden listener")
    public void theProviderConnectsToTheForbiddenListener() {
        BackendEndpoint endpoint = flagd.backendEndpoint();
        String host = endpoint.host(FlagdTckHarness.ENVOY_SERVICE);
        int port = endpoint.port(FlagdTckHarness.ENVOY_SERVICE, FlagdTckHarness.ENVOY_FORBIDDEN_PORT);
        flagd.customize(builder -> builder.host(host).port(port));
    }
}
