package dev.openfeature.contrib.providers.flagd.e2e.steps;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;

import dev.openfeature.contrib.providers.flagd.e2e.State;
import dev.openfeature.sdk.ProviderEvent;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.LinkedList;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;

@Isolated()
public class EventSteps extends AbstractSteps {
    private static final Logger LOG = LoggerFactory.getLogger(EventSteps.class);

    public EventSteps(State state) {
        super(state);
        state.events = new LinkedList<>();
    }

    @Given("a {} event handler")
    public void a_stale_event_handler(String eventType) {
        state.client.on(mapEventType(eventType), eventDetails -> {
           LOG.info("event tracked for {} ", eventType);
            state.events.add(new Event(eventType, eventDetails));
        });
    }

    private static @NotNull ProviderEvent mapEventType(String eventType) {
        switch (eventType) {
            case "stale":
                return ProviderEvent.PROVIDER_STALE;
            case "ready":
                return ProviderEvent.PROVIDER_READY;
            case "error":
                return ProviderEvent.PROVIDER_ERROR;
            case "change":
                return ProviderEvent.PROVIDER_CONFIGURATION_CHANGED;
            default:
                throw new IllegalArgumentException("Unknown event type: " + eventType);
        }
    }

    @When("a {} event was fired")
    public void eventWasFired(String eventType) {
        eventHandlerShouldBeExecutedWithin(eventType, 10000);
    }

    @Then("the {} event handler should have been executed")
    public void eventHandlerShouldBeExecuted(String eventType) {
        eventHandlerShouldBeExecutedWithin(eventType, 30000);
    }

    @Then("the {} event handler should have been executed within {int}ms")
    public void eventHandlerShouldBeExecutedWithin(String eventType, int ms) {
        LOG.info("waiting for eventtype: {}", eventType);
        await().atMost(ms, MILLISECONDS)
                .until(() -> state.events.stream().anyMatch(event -> event.type.equals(eventType)));
        state.lastEvent = state.events.stream()
                .filter(event -> event.type.equals(eventType))
                .findFirst();
        state.events.removeIf(event -> event.type.equals(eventType));
    }
}
