package openfeature.tck.extensions;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.openfeature.contrib.providers.flagd.tck.FlagdTckHarness;
import dev.openfeature.contrib.tools.tck.BackendEndpoint;
import dev.openfeature.contrib.tools.tck.ProviderEventRecord;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.contrib.tools.tck.TckState;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Steps for the connection, event-chain and caching extension scenarios: pointing the provider at
 * the envoy sidecar, and asserting that one event arrived without another before it.
 *
 * <p>Event ordering needs nothing more than that. The TCK's {@code the {} event handler should have
 * been executed} drains the recorded events through the one it matched, so each assertion in a
 * chain waits for an event newer than the previous one. What draining cannot express is an
 * absence between two events: once {@code ready} is matched, an {@code error} recorded before it
 * has been discarded, and {@code the error event handler should not have been executed} passes
 * vacuously. {@link #executedWithNoneBefore} asserts the absence before it drains.
 */
public class ConnectionSteps {

    /** A port nothing listens on, so a provider given a target URI cannot fall back to host and port. */
    private static final int UNAVAILABLE_PORT = 9999;

    private final TckState state;
    private final ProviderTckHarness harness;
    private final FlagdTckHarness flagd;

    public ConnectionSteps(TckState state, ProviderTckHarness harness) {
        this.state = state;
        this.harness = harness;
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

    /**
     * Awaits an event of one kind, asserts that no event of another kind was recorded before it,
     * and then drains through it as the TCK's own await does.
     *
     * @param eventType the event kind to await
     * @param forbiddenType the event kind that must not precede it
     */
    @Then("the {} event handler should have been executed with no {} event before it")
    public void executedWithNoneBefore(String eventType, String forbiddenType) {
        await().alias("provider event " + eventType)
                .atMost(harness.eventTimeout().toMillis(), MILLISECONDS)
                .pollInterval(10, MILLISECONDS)
                .until(() ->
                        state.events.stream().anyMatch(event -> event.type().equals(eventType)));

        List<ProviderEventRecord> before = new ArrayList<>();
        ProviderEventRecord matched = null;
        while (!state.events.isEmpty()) {
            ProviderEventRecord head = state.events.poll();
            if (head.type().equals(eventType)) {
                matched = head;
                break;
            }
            before.add(head);
        }
        state.lastEvent = Optional.ofNullable(matched);

        assertThat(before.stream().map(ProviderEventRecord::type).collect(Collectors.toList()))
                .as("events recorded before the %s event", eventType)
                .doesNotContain(forbiddenType);
    }
}
