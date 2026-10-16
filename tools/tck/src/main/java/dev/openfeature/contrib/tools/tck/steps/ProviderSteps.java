package dev.openfeature.contrib.tools.tck.steps;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.CapabilityGate;
import dev.openfeature.contrib.tools.tck.ProviderTck;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.contrib.tools.tck.TckRuntime;
import dev.openfeature.contrib.tools.tck.TckState;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
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
 * capabilities, creating and registering the provider under test, shutting it down and initialising
 * it again, and simulating backend outages.
 *
 * <p>Every step that touches the backend goes through {@link #backend()}, which is what lets one set
 * of feature files cover a container driven over HTTP and an in-memory provider alike.
 */
public class ProviderSteps extends AbstractSteps {

    private static final Logger log = LoggerFactory.getLogger(ProviderSteps.class);

    public ProviderSteps(TckState state) {
        super(state);
    }

    /** Runs the harness's suite startup once, before the first scenario. */
    @BeforeAll
    public static void beforeAll() {
        TckRuntime.startIfNeeded();
    }

    /** Runs the harness's suite teardown after the last scenario. */
    @AfterAll
    public static void afterAll() {
        TckRuntime.stop();
    }

    /**
     * Skips scenarios that exercise a capability the provider did not declare.
     *
     * <p>Aborting rather than failing means the scenario is reported as <em>skipped</em> by the
     * JUnit Platform, never silently green. It is also how a harness whose
     * {@link dev.openfeature.contrib.tools.tck.BackendControl} cannot simulate an outage stays
     * honest: it leaves {@link Capability#STALE} and {@link Capability#UNAVAILABLE_INIT}
     * undeclared, and the scenarios needing them are skipped here, before any step can reach an
     * unsupported operation.
     *
     * <p>Two tags are <em>failed</em> rather than skipped, and both are failures of this suite
     * rather than of the provider — see {@link CapabilityGate#requireNoExpiredReservation} and
     * {@link CapabilityGate#requireKnownVocabulary}. Only the second reads the scenario's URI, since
     * a feature file under {@link ProviderTck#EXTENSIONS} is expected to carry tags this vocabulary
     * does not know.
     *
     * @param scenario the scenario about to run
     */
    @Before(order = 0)
    public void gateOnCapabilities(Scenario scenario) {
        CapabilityGate.requireDeclared(
                scenario.getUri(), scenario.getSourceTagNames(), harness().capabilities());
    }

    /**
     * Restores the backend to the state every scenario starts from.
     *
     * <p>Scenario isolation is achieved here and nowhere else — never by restarting containers.
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
     * <p>A {@code stable} provider is registered with {@code setProviderAndWait}, so the step does
     * not return until it is ready. An {@code unavailable} provider is registered with
     * {@code setProvider}, deliberately without waiting — the scenario's whole point is that
     * readiness never arrives.
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

        // A conformance report identifies the provider by what it calls itself, not by the suite
        // name, and this is the only place that knows it.
        String providerName = provider.getMetadata().getName();
        TckRuntime.get().recordProviderName(providerName);

        log.info("Registered {} provider {} under domain {}", flavour, providerName, domain);
    }

    /**
     * Asserts that the provider under test identifies itself by name.
     *
     * <p>Requirement 2.1.1. Asserted because a conformance report keyed on the provider's metadata
     * name cannot be attributed to anything when that name is empty.
     */
    @Then("the provider metadata name should not be empty")
    public void theProviderMetadataNameShouldNotBeEmpty() {
        Metadata metadata = requireProvider().getMetadata();
        assertThat(metadata).as("provider metadata").isNotNull();
        assertThat(metadata.getName()).as("provider metadata name").isNotBlank();
    }

    /**
     * Shuts the provider under test down by calling its own {@code shutdown()} directly.
     *
     * <p>Directly, and not by replacing it through the SDK: {@code setProvider} would shut the old
     * provider down too, but that tests the SDK's bookkeeping as much as the provider's. The
     * provider stays registered and the SDK is not told, which is what lets
     * {@code the provider is initialized again} be observed through the same client afterwards.
     *
     * <p>Timed, because one scenario asserts that shutdown against a backend that will never answer
     * returns at all rather than blocking on a graceful close. Exceptions are recorded rather than
     * propagated, exactly as an evaluation's are, so that {@code no exception should have been
     * thrown} covers the double-shutdown case.
     */
    @When("the provider is shut down")
    public void theProviderIsShutDown() {
        FeatureProvider provider = requireProvider();
        long started = System.nanoTime();
        try {
            provider.shutdown();
        } catch (RuntimeException e) {
            log.warn("shutdown() of provider {} threw", provider.getMetadata().getName(), e);
            state.thrown = e;
            state.thrownBy = "shutdown()";
        } finally {
            state.shutdownDuration = Duration.ofNanos(System.nanoTime() - started);
        }
    }

    /**
     * Initialises the provider under test again by calling its own {@code initialize()} directly.
     *
     * <p>The SDK still holds the provider as {@code READY}, because it was never told about the
     * shutdown, so the evaluation that follows this step reaches the re-initialised provider through
     * the scenario's client with nothing in between. Exceptions are recorded rather than propagated,
     * the same way an evaluation's are.
     */
    @When("the provider is initialized again")
    public void theProviderIsInitializedAgain() {
        FeatureProvider provider = requireProvider();
        try {
            provider.initialize(state.context);
        } catch (Exception e) {
            log.warn(
                    "initialize() of provider {} threw after shutdown",
                    provider.getMetadata().getName(),
                    e);
            state.thrown = e;
            state.thrownBy = "initialize()";
        }
    }

    /**
     * Asserts that the most recent {@code the provider is shut down} returned within a bound.
     *
     * <p>A shutdown that waits for a graceful close of a connection that will never answer hangs the
     * host application's own shutdown. The bound in the feature file is generous: what is asserted
     * is that shutdown returns at all.
     *
     * @param milliseconds the bound
     */
    @Then("the shutdown should have completed within {int}ms")
    public void theShutdownShouldHaveCompletedWithin(int milliseconds) {
        assertThat(state.shutdownDuration)
                .as("a shutdown was recorded; did the scenario forget 'When the provider is shut down'?")
                .isNotNull();
        assertThat(state.shutdownDuration.toMillis())
                .withFailMessage(
                        "shutdown() took %dms, over the %dms bound. A shutdown must not block on a backend "
                                + "that will never answer; release what initialisation acquired and return.",
                        state.shutdownDuration.toMillis(), milliseconds)
                .isLessThanOrEqualTo(milliseconds);
    }

    /** Makes the backend unreachable for the rest of the scenario. */
    @When("the connection is lost")
    public void theConnectionIsLost() {
        backend().disconnect();
    }

    /**
     * Brings the backend back after {@code the connection is lost}.
     *
     * <p>An explicit end to the outage, rather than a self-healing "lost for {@code N}s" form that
     * would race the assertion, is what makes the stale-then-ready transition deterministic. It is
     * also why no shipped scenario reaches {@code POST /restart} and this suite binds no step to it.
     */
    @When("the connection is restored")
    public void theConnectionIsRestored() {
        backend().reconnect();
    }

    /** Mutates flag configuration so a conforming provider observes a configuration change. */
    @When("the flag was modified")
    public void theFlagWasModified() {
        backend().changeFlag();
    }

    /**
     * Asserts the provider settles into the expected lifecycle state.
     *
     * <p>Awaits rather than asserting immediately: state transitions are asynchronous, and how
     * quickly a provider notices a backend change varies by orders of magnitude, so the timeout
     * comes from {@link ProviderTckHarness#readyTimeout()}.
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

    private FeatureProvider requireProvider() {
        if (state.provider == null) {
            throw new AssertionError("No provider has been created. "
                    + "Did the scenario forget 'Given a stable provider' or 'Given a unavailable provider'?");
        }
        return state.provider;
    }
}
