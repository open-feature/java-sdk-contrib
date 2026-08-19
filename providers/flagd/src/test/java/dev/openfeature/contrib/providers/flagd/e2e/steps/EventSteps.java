package dev.openfeature.contrib.providers.flagd.e2e.steps;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.openfeature.contrib.providers.flagd.e2e.State;
import dev.openfeature.sdk.ProviderEvent;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class EventSteps extends AbstractSteps {

    private static final int EVENT_TIMEOUT_MS = 12_000;

    public EventSteps(State state) {
        super(state);
    }

    @Given("a {} event handler")
    public void a_stale_event_handler(String eventType) {
        state.client.on(mapEventType(eventType), eventDetails -> {
            log.info("{} event tracked", eventType);
            Event event = new Event(eventType, eventDetails);
            state.assertedEvents.add(event);
            state.allEvents.add(event);
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
        eventHandlerShouldBeExecutedWithin(eventType, EVENT_TIMEOUT_MS);
    }

    @Then("the {} event handler should have been executed")
    public void eventHandlerShouldBeExecuted(String eventType) {
        eventHandlerShouldBeExecutedWithin(eventType, EVENT_TIMEOUT_MS);
    }

    @Then("the {} event handler should not have been executed")
    public void eventHandlerShouldNotHaveBeenExecuted(String eventType) {
        // checks the full log, not assertedEvents: preceding positive
        // assertions may have drained an intervening event of this type
        assertThat(state.allEvents.stream().anyMatch(event -> event.type.equals(eventType)))
                .as("no %s event should have fired", eventType)
                .isFalse();
    }

    @Then("the {} event handler should have been executed within {int}ms")
    public void eventHandlerShouldBeExecutedWithin(String eventType, int ms) {
        log.info("waiting for eventtype: {}", eventType);
        await().alias("waiting for eventtype " + eventType)
                .atMost(ms, MILLISECONDS)
                .pollInterval(10, MILLISECONDS)
                .until(() -> state.assertedEvents.stream().anyMatch(event -> event.type.equals(eventType)));
        // Drain all events up to and including the first match. This ensures that
        // older events (e.g. a READY from before a disconnect) cannot satisfy a
        // later assertion that expects a *new* event of the same type, while still
        // preserving events that arrived *after* the match for subsequent steps.
        Event matched = null;
        while (!state.assertedEvents.isEmpty()) {
            Event head = state.assertedEvents.poll();
            if (head != null && head.type.equals(eventType)) {
                matched = head;
                break;
            }
        }
        state.lastEvent = java.util.Optional.ofNullable(matched);
    }
}
