package dev.openfeature.contrib.tools.providertck.steps;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;

import dev.openfeature.contrib.tools.providertck.Capability;
import dev.openfeature.contrib.tools.providertck.CapabilityGate;
import dev.openfeature.contrib.tools.providertck.ProviderTckHarness;
import dev.openfeature.contrib.tools.providertck.TckRuntime;
import dev.openfeature.contrib.tools.providertck.TckState;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.NoOpProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderState;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle and backend-control steps: bringing the suite up, gating scenarios on declared
 * capabilities, creating and registering the provider under test, and simulating backend outages.
 *
 * <p>Every step that touches the backend goes through {@link #backend()}. Nothing here knows whether
 * that is a container driven over HTTP or an in-memory provider manipulated directly, which is what
 * lets one set of feature files cover both.
 *
 * <p>Step vocabulary is inherited from the flagd test harness so that existing feature files port
 * with a near-zero diff. The only change is dropping the word {@code flagd} from the provider setup
 * step: {@code a stable flagd provider} becomes {@code a stable provider}.
 */
public class ProviderSteps extends AbstractSteps {

    private static final Logger log = LoggerFactory.getLogger(ProviderSteps.class);

    public ProviderSteps(TckState state) {
        super(state);
    }

    /**
     * Runs the harness's suite startup once, before the first scenario.
     */
    @BeforeAll
    public static void beforeAll() {
        TckRuntime.startIfNeeded();
    }

    /**
     * Runs the harness's suite teardown after the last scenario.
     */
    @AfterAll
    public static void afterAll() {
        TckRuntime.stop();
    }

    /**
     * Skips scenarios that exercise a capability the provider did not declare.
     *
     * <p>Aborting rather than failing means the scenario is reported as <em>skipped</em> by the
     * JUnit Platform. That distinction is the whole point: a provider that does not support
     * configuration-change events should see those scenarios visibly excluded, never silently
     * green.
     *
     * <p>This is also how a backend with no connection to lose stays honest. A harness whose
     * {@link dev.openfeature.contrib.tools.providertck.BackendControl} cannot simulate an outage
     * leaves {@link Capability#STALE} and {@link Capability#UNAVAILABLE_INIT} undeclared, and the
     * scenarios needing them are skipped here — before any step can reach an unsupported operation.
     *
     * @param scenario the scenario about to run
     */
    @Before(order = 0)
    public void gateOnCapabilities(Scenario scenario) {
        CapabilityGate.requireDeclared(scenario.getSourceTagNames(), harness().capabilities());
    }

    /**
     * Restores the backend to the state every scenario starts from.
     *
     * <p>Scenario isolation is achieved here and nowhere else — never by restarting containers, and
     * never by relying on scenarios happening not to interfere.
     */
    @Before(order = 10)
    public void prepareBackend() {
        backend().prepareScenario();
    }

    /**
     * Tears the provider down without disturbing the backend.
     *
     * <p>Replaces the domain's provider with a {@link NoOpProvider} through the SDK lifecycle rather
     * than calling {@code shutdown()} directly, because only the former makes the SDK detach the
     * event provider and shut down its emitter executor. Skipping this leaks an emitter thread per
     * scenario and lets events from a finished scenario surface in the next one.
     */
    @After
    public void tearDown() {
        if (state.domain != null) {
            OpenFeatureAPI.getInstance().setProvider(state.domain, new NoOpProvider());
        }
    }

    /**
     * Creates the provider under test and registers it under a scenario-scoped domain.
     *
     * <p>Two provider flavours are recognised. A {@code stable} provider is built by the harness
     * against the running backend and registered with {@code setProviderAndWait}, so the step does
     * not return until the provider is ready. An {@code unavailable} provider points at a dead
     * backend and is registered with {@code setProvider}, deliberately without waiting — the
     * scenario's whole point is that readiness never arrives.
     *
     * @param flavour either {@code stable} or {@code unavailable}
     */
    @Given("a {} provider")
    public void createProvider(String flavour) {
        ProviderTckHarness harness = harness();
        FeatureProvider provider;
        boolean waitForReady;

        switch (flavour) {
            case "stable":
                provider = harness.createProvider();
                waitForReady = true;
                break;
            case "unavailable":
                provider = harness.createUnavailableProvider();
                waitForReady = false;
                break;
            default:
                throw new IllegalArgumentException(
                        "Unknown provider flavour '" + flavour + "'. The TCK recognises 'stable' and 'unavailable'.");
        }

        String domain = "tck-" + UUID.randomUUID();
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        if (waitForReady) {
            api.setProviderAndWait(domain, provider);
        } else {
            api.setProvider(domain, provider);
        }

        state.provider = provider;
        state.domain = domain;
        state.client = api.getClient(domain);
        log.info(
                "Registered {} provider {} under domain {}",
                flavour,
                provider.getMetadata().getName(),
                domain);
    }

    /**
     * Makes the backend unreachable for the rest of the scenario.
     */
    @When("the connection is lost")
    public void theConnectionIsLost() {
        backend().disconnect();
    }

    /**
     * Makes the backend unreachable for a bounded period, then brings it back.
     *
     * @param seconds how long the backend stays unreachable
     */
    @When("the connection is lost for {int}s")
    public void theConnectionIsLostFor(int seconds) {
        backend().disconnectFor(Duration.ofSeconds(seconds));
    }

    /**
     * Brings the backend back after {@code the connection is lost}.
     *
     * <p>Added by the TCK. The flagd harness only has the self-healing
     * {@code the connection is lost for {int}s} form, which cannot express "assert the provider is
     * stale, and only then reconnect" — the reconnect races the assertion. Splitting the outage
     * into an explicit start and end makes the stale-then-ready transition deterministic.
     */
    @When("the connection is restored")
    public void theConnectionIsRestored() {
        backend().reconnect();
    }

    /**
     * Mutates flag configuration so a conforming provider observes a configuration change.
     */
    @When("the flag was modified")
    public void theFlagWasModified() {
        backend().changeFlag();
    }

    /**
     * Asserts the provider settles into the expected lifecycle state.
     *
     * <p>Awaits rather than asserting immediately. State transitions are asynchronous in every
     * provider, and how quickly one notices a backend change varies by orders of magnitude between
     * streaming and polling transports, so the timeout comes from
     * {@link ProviderTckHarness#readyTimeout()}.
     *
     * @param expected the expected {@link ProviderState}, case-insensitive
     */
    @Then("the client should be in {} state")
    public void theClientShouldBeInState(String expected) {
        ProviderState target = ProviderState.valueOf(expected.toUpperCase());
        await().alias("provider state " + target)
                .atMost(harness().readyTimeout().toMillis(), MILLISECONDS)
                .pollInterval(10, MILLISECONDS)
                .until(() -> state.client.getProviderState() == target);
    }
}
