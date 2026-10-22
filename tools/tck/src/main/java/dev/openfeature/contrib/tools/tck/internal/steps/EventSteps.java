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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers provider event handlers and awaits events, defaulting to
 * {@link ProviderTckHarness#eventTimeout()}.
 */
public class EventSteps extends AbstractSteps {

    private static final Logger log = LoggerFactory.getLogger(EventSteps.class);

    // Awaitility wants a deadline past the poll delay; this only covers the assertion itself.
    private static final long SETTLE_SLACK_MS = 250;

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
     * Asserts that no event of the given kind arrives, within
     * {@link ProviderTckHarness#eventTimeout()}.
     *
     * @param eventType the event kind
     */
    @Then("the {} event handler should not have been executed")
    public void theEventHandlerShouldNotHaveBeenExecuted(String eventType) {
        awaitNoEvent(eventType, harness().eventTimeout().toMillis());
    }

    /**
     * Asserts that no event of the given kind arrives within an explicit window.
     *
     * @param eventType the event kind
     * @param milliseconds how long to watch for it
     */
    @Then("the {} event handler should not have been executed within {int}ms")
    public void theEventHandlerShouldNotHaveBeenExecutedWithin(String eventType, int milliseconds) {
        awaitNoEvent(eventType, milliseconds);
    }

    /**
     * Watches for the whole window before answering, because at any single instant absence and
     * lateness are the same observation: asserting immediately states "no such event <em>yet</em>",
     * which a provider one scheduler slice slower satisfies while the scenario means it never fires.
     * The positive form awaits, so the negative form has to watch at least as long to be its
     * opposite.
     */
    private void awaitNoEvent(String eventType, long milliseconds) {
        log.info("Watching for no {} event ({}ms)", eventType, milliseconds);
        await().alias("no provider event " + eventType)
                .pollDelay(milliseconds, MILLISECONDS)
                .atMost(milliseconds + SETTLE_SLACK_MS, MILLISECONDS)
                .untilAsserted(() -> assertThat(state.events.stream()
                                .filter(event -> event.type().equals(eventType))
                                .findFirst())
                        .as("no %s event should have fired within %dms", eventType, milliseconds)
                        .isEmpty());
    }

    private void awaitEvent(String eventType, long milliseconds) {
        log.info("Awaiting {} event (timeout {}ms)", eventType, milliseconds);
        await().alias("provider event " + eventType)
                .atMost(milliseconds, MILLISECONDS)
                .pollInterval(10, MILLISECONDS)
                .until(() ->
                        state.events.stream().anyMatch(event -> event.type().equals(eventType)));

        // Takes the matched record and nothing else. Draining through the match also discarded
        // records of other kinds, which a later assertion is entitled to see: an error event that
        // arrived before an awaited change event would be gone, and "the error event handler should
        // not have been executed" would then pass because the evidence had been thrown away.
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
