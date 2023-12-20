package dev.openfeature.contrib.providers.flagd.e2e.reconnect.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.function.Consumer;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Isolated;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EventDetails;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Test suite for testing flagd provider reconnect functionality.
 * The associated container run a flagd instance which restarts every 5s. 
 */
@Isolated()
@Order(value = Integer.MAX_VALUE)
public class StepDefinitions {

    private static Client client;
    private static FeatureProvider provider;

    private int readyHandlerRunCount = 0;
    private int errorHandlerRunCount = 0;

    private Consumer<EventDetails> readyHandler = (EventDetails) -> {
        readyHandlerRunCount++;
    };
    private Consumer<EventDetails> errorHandler = (EventDetails) -> {
        errorHandlerRunCount++;
    };

    /**
     * Injects the client to use for this test.
     * Tests run one at a time, but just in case, a lock is used to make sure the
     * client is not updated mid-test.
     * 
     * @param client client to inject into test.
     */
    public static void setProvider(FeatureProvider provider) {
        StepDefinitions.provider = provider;
    }

    public StepDefinitions() {
        StepDefinitions.client = OpenFeatureAPI.getInstance().getClient("unstable");
        client.onProviderReady(this.readyHandler);
        client.onProviderError(this.errorHandler);
        OpenFeatureAPI.getInstance().setProviderAndWait("unstable", provider);
    }

    @Given("a flagd provider is set")
    public static void setup() {
        // done in constructor
    }

    @AfterAll()
    public static void cleanUp() throws InterruptedException {
        StepDefinitions.provider.shutdown();
        StepDefinitions.provider = null;
        StepDefinitions.client = null;
    }

    @When("a PROVIDER_READY handler and a PROVIDER_ERROR handler are added")
    public void a_provider_ready_handler_and_a_provider_error_handler_are_added() {
        // done in constructor
    }

    @Then("the PROVIDER_READY handler must run when the provider connects")
    public void the_provider_ready_handler_must_run_when_the_provider_connects() {
         // should already be at 1 from initial connection
        assertEquals(1, readyHandlerRunCount);
    }

    @Then("the PROVIDER_ERROR handler must run when the provider's connection is lost")
    public void the_provider_error_handler_must_run_when_the_provider_s_connection_is_lost() {
        // wait up to 15 seconds for a disconnect (PROVIDER_ERROR event)
        Awaitility.await().atMost(Duration.ofSeconds(15))
        .until(() -> {
            return this.errorHandlerRunCount > 0;
        });
    }

    @Then("when the connection is reestablished the PROVIDER_READY handler must run again")
    public void when_the_connection_is_reestablished_the_provider_ready_handler_must_run_again() {
        // wait up to 15 seconds for a reconnect (PROVIDER_READY event)
        Awaitility.await().atMost(Duration.ofSeconds(15))
        .until(() -> {
            return this.readyHandlerRunCount > 1;
        });
    }
}