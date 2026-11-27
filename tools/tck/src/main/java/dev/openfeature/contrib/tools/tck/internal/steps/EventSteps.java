package dev.openfeature.contrib.tools.tck.internal.steps;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.openfeature.contrib.tools.tck.BackendControl;
import dev.openfeature.contrib.tools.tck.ProviderEventRecord;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.contrib.tools.tck.TckState;
import dev.openfeature.sdk.ProviderEvent;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers provider event handlers and awaits events, defaulting to
 * {@link ProviderTckHarness#eventTimeout()}.
 */
public class EventSteps extends AbstractSteps {

    private static final Logger log = LoggerFactory.getLogger(EventSteps.class);

    public EventSteps(TckState state, ProviderTckHarness harness, BackendControl backend) {
        super(state, harness, backend);
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
     * @param eventType the event kind
     * @param milliseconds the deadline
     */
    @Then("the {} event handler should have been executed within {int}ms")
    public void theEventHandlerShouldHaveBeenExecutedWithin(String eventType, int milliseconds) {
        awaitEvent(eventType, milliseconds);
    }

    /**
     * Awaits an event of one kind and asserts that no event of another kind was recorded before it.
     *
     * <p>This is how a scenario says "and no error": as an <em>order</em>, not as a window. "No
     * error event fired" can only be answered for some stretch of time, and at any instant an event
     * that never fires and one that fires a moment later are the same observation -- so a step
     * asking it that way either slows every run down or passes because nothing has arrived yet.
     * "No error before the provider came back ready" is a question about what was recorded, and the
     * recording answers it with no clock involved.
     *
     * @param eventType the event kind to await
     * @param forbiddenType the event kind that must not have been recorded before it
     */
    @Then("the {} event handler should have been executed with no {} event before it")
    public void theEventHandlerShouldHaveBeenExecutedWithNoneBefore(String eventType, String forbiddenType) {
        log.info("Awaiting {} event with no {} event before it", eventType, forbiddenType);
        awaitRecorded(eventType, harness().eventTimeout().toMillis());

        List<String> before = new ArrayList<>();
        for (ProviderEventRecord record : state.events) {
            if (record.type().equals(eventType)) {
                break;
            }
            before.add(record.type());
        }
        assertThat(before).as("events recorded before the %s event", eventType).doesNotContain(forbiddenType);
        takeEvent(eventType);
    }

    private void awaitEvent(String eventType, long milliseconds) {
        log.info("Awaiting {} event (timeout {}ms)", eventType, milliseconds);
        awaitRecorded(eventType, milliseconds);
        takeEvent(eventType);
    }

    private void awaitRecorded(String eventType, long milliseconds) {
        await().alias("provider event " + eventType)
                .atMost(milliseconds, MILLISECONDS)
                .pollInterval(10, MILLISECONDS)
                .until(() ->
                        state.events.stream().anyMatch(event -> event.type().equals(eventType)));
    }

    /**
     * Takes the matched record and nothing else.
     *
     * <p>Draining through the match would also discard records of other kinds, which a later
     * assertion is entitled to see: an error event recorded before an awaited ready event would be
     * gone, and the order assertion above would find nothing left to object to.
     */
    private void takeEvent(String eventType) {
        ProviderEventRecord matched = state.events.stream()
                .filter(event -> event.type().equals(eventType))
                .findFirst()
                .orElse(null);
        if (matched != null) {
            // removing only this record keeps a stale one of the same kind from a later await
            state.events.remove(matched);
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
