package dev.openfeature.contrib.tools.providertck.steps;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;

import dev.openfeature.contrib.tools.providertck.ProviderEventRecord;
import dev.openfeature.contrib.tools.providertck.ProviderTckHarness;
import dev.openfeature.contrib.tools.providertck.TckState;
import dev.openfeature.sdk.ProviderEvent;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registration of provider event handlers and awaiting the events they observe.
 *
 * <p>Step vocabulary is inherited verbatim from the flagd test harness. The one behavioural change
 * is that the default await timeout comes from {@link ProviderTckHarness#eventTimeout()} instead of
 * being a hard-coded constant, because how fast a provider notices a backend change differs by
 * orders of magnitude between streaming and polling transports.
 */
public class EventSteps extends AbstractSteps {

    private static final Logger log = LoggerFactory.getLogger(EventSteps.class);

    public EventSteps(TckState state) {
        super(state);
    }

    /**
     * Registers a handler for one kind of provider event.
     *
     * @param eventType one of {@code ready}, {@code error}, {@code stale} or {@code change}
     */
    @Given("a {} event handler")
    public void registerEventHandler(String eventType) {
        state.client.on(mapEventType(eventType), details -> {
            log.info("{} event observed", eventType);
            state.events.add(new ProviderEventRecord(eventType, details));
        });
    }

    /**
     * Awaits an event of the given kind, using the provider's configured timeout.
     *
     * @param eventType the event kind
     */
    @When("a {} event was fired")
    public void eventWasFired(String eventType) {
        awaitEvent(eventType, harness().eventTimeout().toMillis());
    }

    /**
     * Awaits an event of the given kind, using the provider's configured timeout.
     *
     * @param eventType the event kind
     */
    @Then("the {} event handler should have been executed")
    public void theEventHandlerShouldHaveBeenExecuted(String eventType) {
        awaitEvent(eventType, harness().eventTimeout().toMillis());
    }

    /**
     * Awaits an event of the given kind within an explicit deadline.
     *
     * <p>Use this where the deadline is part of what the scenario asserts — for instance that a
     * provider initialised against a dead backend reports the failure promptly rather than hanging.
     * The explicit value always wins over {@link ProviderTckHarness#eventTimeout()}.
     *
     * @param eventType the event kind
     * @param milliseconds the deadline
     */
    @Then("the {} event handler should have been executed within {int}ms")
    public void theEventHandlerShouldHaveBeenExecutedWithin(String eventType, int milliseconds) {
        awaitEvent(eventType, milliseconds);
    }

    private void awaitEvent(String eventType, long milliseconds) {
        log.info("Awaiting {} event (timeout {}ms)", eventType, milliseconds);
        await().alias("provider event " + eventType)
                .atMost(milliseconds, MILLISECONDS)
                .pollInterval(10, MILLISECONDS)
                .until(() ->
                        state.events.stream().anyMatch(event -> event.type().equals(eventType)));

        // Drain up to and including the first match. Without this, a READY recorded before a
        // disconnect would satisfy a later assertion expecting a *new* READY after reconnect,
        // and the reconnect scenarios would pass without the provider ever reconnecting.
        // Events that arrived after the match are preserved for subsequent steps.
        ProviderEventRecord matched = null;
        while (!state.events.isEmpty()) {
            ProviderEventRecord head = state.events.poll();
            if (head != null && head.type().equals(eventType)) {
                matched = head;
                break;
            }
        }
        state.lastEvent = Optional.ofNullable(matched);
    }

    private static ProviderEvent mapEventType(String eventType) {
        switch (eventType) {
            case "ready":
                return ProviderEvent.PROVIDER_READY;
            case "error":
                return ProviderEvent.PROVIDER_ERROR;
            case "stale":
                return ProviderEvent.PROVIDER_STALE;
            case "change":
                return ProviderEvent.PROVIDER_CONFIGURATION_CHANGED;
            default:
                throw new IllegalArgumentException(
                        "Unknown event type '" + eventType + "'. The TCK recognises ready, error, stale and change.");
        }
    }
}
