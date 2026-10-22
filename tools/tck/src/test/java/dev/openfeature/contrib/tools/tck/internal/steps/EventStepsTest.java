package dev.openfeature.contrib.tools.tck.internal.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.openfeature.contrib.tools.tck.BackendControl;
import dev.openfeature.contrib.tools.tck.InProcessControl;
import dev.openfeature.contrib.tools.tck.ProviderEventRecord;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.contrib.tools.tck.TckState;
import dev.openfeature.sdk.FeatureProvider;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies what {@code should not have been executed} asserts.
 *
 * <p>The point of these is the window. A negative assertion taken at one instant cannot tell an
 * event that never fires from one that fires a moment later, and the scenarios that use it mean the
 * first -- so the step has to watch, and a late event has to fail it.
 */
class EventStepsTest {

    private static final String ERROR = "error";

    /** A harness that brings nothing up and answers a short event timeout. */
    private static final class StubHarness implements ProviderTckHarness {

        private final InProcessControl control = BackendControl.inProcess();

        private final Duration eventTimeout;

        private StubHarness(Duration eventTimeout) {
            this.eventTimeout = eventTimeout;
        }

        @Override
        public FeatureProvider createProvider() {
            return control.createProvider();
        }

        @Override
        public BackendControl backendControl() {
            return control;
        }

        @Override
        public Duration eventTimeout() {
            return eventTimeout;
        }
    }

    private static EventSteps stepsWith(TckState state, Duration eventTimeout) {
        StubHarness harness = new StubHarness(eventTimeout);
        return new EventSteps(state, harness, harness.backendControl());
    }

    @Test
    @DisplayName("an event that never fires passes, and the window is actually waited out")
    void anAbsenceIsWatchedForTheWholeWindow() {
        TckState state = new TckState();
        EventSteps steps = stepsWith(state, Duration.ofMillis(300));

        long started = System.nanoTime();
        assertThatCode(() -> steps.theEventHandlerShouldNotHaveBeenExecutedWithin(ERROR, 300))
                .doesNotThrowAnyException();

        assertThat(Duration.ofNanos(System.nanoTime() - started))
                .as("asserting before the window elapses would only say 'not yet'")
                .isGreaterThanOrEqualTo(Duration.ofMillis(300));
    }

    @Test
    @DisplayName("an event that fires late inside the window fails the assertion")
    void aLateEventIsNotMistakenForAbsence() {
        TckState state = new TckState();
        EventSteps steps = stepsWith(state, Duration.ofMillis(600));
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            state.events.add(new ProviderEventRecord(ERROR, null));
        });

        assertThatThrownBy(() -> steps.theEventHandlerShouldNotHaveBeenExecutedWithin(ERROR, 600))
                .as("the instant form passed here, which is the bug this step was given a window for")
                .hasMessageContaining("no error event should have fired");
    }

    @Test
    @DisplayName("an event already recorded fails, naming the kind and the window")
    void anEventAlreadyRecordedFails() {
        TckState state = new TckState();
        state.events.add(new ProviderEventRecord(ERROR, null));
        EventSteps steps = stepsWith(state, Duration.ofMillis(200));

        assertThatThrownBy(() -> steps.theEventHandlerShouldNotHaveBeenExecutedWithin(ERROR, 200))
                .hasMessageContaining("no error event should have fired within 200ms");
    }

    @Test
    @DisplayName("awaiting one kind of event does not throw away another kind's record")
    void aPositiveAwaitDoesNotEraseTheEvidence() {
        TckState state = new TckState();
        EventSteps steps = stepsWith(state, Duration.ofMillis(200));
        state.events.add(new ProviderEventRecord(ERROR, null));
        state.events.add(new ProviderEventRecord("change", null));

        steps.theEventHandlerShouldHaveBeenExecuted("change");

        assertThatThrownBy(() -> steps.theEventHandlerShouldNotHaveBeenExecutedWithin(ERROR, 200))
                .as("the error arrived first; awaiting the change must not make it unobservable")
                .hasMessageContaining("no error event should have fired");
    }

    @Test
    @DisplayName("another kind of event does not fail it")
    void anotherKindOfEventIsIgnored() {
        TckState state = new TckState();
        state.events.add(new ProviderEventRecord("ready", null));
        EventSteps steps = stepsWith(state, Duration.ofMillis(200));

        assertThatCode(() -> steps.theEventHandlerShouldNotHaveBeenExecutedWithin(ERROR, 200))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the step without a window watches for the harness's event timeout")
    void theDefaultWindowIsTheHarnessEventTimeout() {
        TckState state = new TckState();
        EventSteps steps = stepsWith(state, Duration.ofMillis(400));

        long started = System.nanoTime();
        assertThatCode(() -> steps.theEventHandlerShouldNotHaveBeenExecuted(ERROR))
                .doesNotThrowAnyException();

        assertThat(Duration.ofNanos(System.nanoTime() - started))
                .as("the pair has to be symmetrical: the positive form awaits this same timeout")
                .isGreaterThanOrEqualTo(Duration.ofMillis(400));
    }
}
