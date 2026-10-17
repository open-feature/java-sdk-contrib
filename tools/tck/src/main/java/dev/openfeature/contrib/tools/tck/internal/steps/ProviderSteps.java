package dev.openfeature.contrib.tools.tck.internal.steps;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.BackendControl;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.contrib.tools.tck.ProviderTck;
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
 * Lifecycle, capability gating, provider registration, and backend-control steps.
 */
public class ProviderSteps extends AbstractSteps {

    private static final Logger log = LoggerFactory.getLogger(ProviderSteps.class);

    public ProviderSteps(TckState state, ProviderTckHarness harness, BackendControl backend) {
        super(state, harness, backend);
    }

    /** Opens the suite, and rejects an undeclarable capability set before any backend starts. */
    @BeforeAll
    public static void beforeAll() {
        TckLifecycle.beginSuite();
    }

    /** Stops every backend the suite's scenarios started, on whichever thread started it. */
    @AfterAll
    public static void afterAll() {
        TckLifecycle.endSuite();
    }

    /**
     * Skips scenarios needing an undeclared {@link Capability}; see
     * {@link Capability#requireDeclared(java.net.URI, java.util.Collection, java.util.Set)}. Tags
     * under {@link ProviderTck#EXTENSIONS} are exempt from the vocabulary check.
     *
     * @param scenario the scenario about to run
     */
    @Before(order = 0)
    public void gateOnCapabilities(Scenario scenario) {
        Capability.requireDeclared(
                scenario.getUri(), scenario.getSourceTagNames(), harness().capabilities());
    }

    /**
     * Restores the backend to the state every scenario starts from.
     */
    @Before(order = 10)
    public void prepareBackend() {
        backend().prepareScenario();
    }

    /**
     * Replaces the provider with a {@link NoOpProvider} via the SDK so its emitter is shut down.
     */
    @After
    public void tearDown() {
        if (state.domain != null) {
            OpenFeatureAPI.getInstance().setProvider(state.domain, new NoOpProvider());
        }
    }

    /**
     * Creates the provider under test and registers it under a scenario-scoped domain; only
     * {@code stable} waits for readiness.
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
     * Asserts that the provider metadata name is not empty (requirement 2.1.1).
     */
    @Then("the provider metadata name should not be empty")
    public void theProviderMetadataNameShouldNotBeEmpty() {
        Metadata metadata = requireProvider().getMetadata();
        assertThat(metadata).as("provider metadata").isNotNull();
        assertThat(metadata.getName()).as("provider metadata name").isNotBlank();
    }

    /**
     * Calls the provider's {@code shutdown()} directly, timing it and recording any exception.
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
     * Calls the provider's {@code initialize()} directly, recording any exception.
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
     * Asserts that the last shutdown returned within a bound.
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
                        "shutdown() took %dms, over the %dms bound; it must not block on an unresponsive backend.",
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
     * Awaits the expected provider state within {@link ProviderTckHarness#readyTimeout()}.
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
