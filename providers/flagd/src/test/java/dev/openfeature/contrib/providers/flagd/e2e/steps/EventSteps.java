package dev.openfeature.contrib.providers.flagd.e2e.steps;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;

import dev.openfeature.contrib.providers.flagd.e2e.State;
import dev.openfeature.sdk.ProviderEvent;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.LinkedList;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated()
@Slf4j
public class EventSteps extends AbstractSteps {

    public EventSteps(State state) {
        super(state);
        state.events = new LinkedList<>();
    }

    @Given("a {} event handler")
    public void a_stale_event_handler(String eventType) {
        state.client.on(mapEventType(eventType), eventDetails -> {
            log.info("event tracked for {} at {} ms ", eventType, System.currentTimeMillis()%100_000);
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
    public void eventWasFired(String eventType) throws InterruptedException {
        eventHandlerShouldBeExecutedWithin(eventType, 8000);
    }

    @Then("the {} event handler should have been executed")
    public void eventHandlerShouldBeExecuted(String eventType) {
        eventHandlerShouldBeExecutedWithin(eventType, 10000);
    }

    @Then("the {} event handler should have been executed within {int}ms")
    public void eventHandlerShouldBeExecutedWithin(String eventType, int ms) {
        log.info("waiting for eventtype: {}", eventType);
        await().atMost(ms, MILLISECONDS)
                .pollInterval(10, MILLISECONDS)
                .until(() -> state.events.stream().anyMatch(event -> event.type.equals(eventType)));
        state.lastEvent = state.events.stream()
                .filter(event -> event.type.equals(eventType))
                .findFirst();
        state.events.removeIf(event -> event.type.equals(eventType));
    }
}
