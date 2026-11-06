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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the event assertions read, and what they leave behind.
 *
 * <p>Both halves matter to a chain like stale, then error, then ready: each assertion has to see the
 * records an earlier one did not claim, or an ordering question is answered from an emptied queue.
 */
class EventStepsTest {

    private static final String READY = "ready";

    private static final String ERROR = "error";

    /** A harness that brings nothing up and answers a short event timeout. */
    private static final class StubHarness implements ProviderTckHarness {

        private final InProcessControl control = BackendControl.inProcess();

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
            return Duration.ofMillis(300);
        }
    }

    private static EventSteps stepsWith(TckState state) {
        StubHarness harness = new StubHarness();
        return new EventSteps(state, harness, harness.backendControl());
    }

    @Test
    @DisplayName("an event of the forbidden kind recorded first fails the ordering")
    void anEarlierForbiddenEventFailsTheOrdering() {
        TckState state = new TckState();
        state.events.add(new ProviderEventRecord(ERROR, null));
        state.events.add(new ProviderEventRecord(READY, null));
        EventSteps steps = stepsWith(state);

        assertThatThrownBy(() -> steps.theEventHandlerShouldHaveBeenExecutedWithNoneBefore(READY, ERROR))
                .as("the error was recorded before the ready, which is what the scenario forbids")
                .hasMessageContaining("events recorded before the ready event");
    }

    @Test
    @DisplayName("the same records in the other order pass, and the ready is consumed")
    void theOrderingPassesWhenTheForbiddenEventComesAfter() {
        TckState state = new TckState();
        state.events.add(new ProviderEventRecord(READY, null));
        state.events.add(new ProviderEventRecord(ERROR, null));
        EventSteps steps = stepsWith(state);

        assertThatCode(() -> steps.theEventHandlerShouldHaveBeenExecutedWithNoneBefore(READY, ERROR))
                .doesNotThrowAnyException();

        assertThat(state.lastEvent).get().extracting(ProviderEventRecord::type).isEqualTo(READY);
        assertThat(state.events)
                .as("the ready was claimed; the error is still there for whatever asks next")
                .extracting(ProviderEventRecord::type)
                .containsExactly(ERROR);
    }

    @Test
    @DisplayName("an ordering question with nothing recorded at all times out rather than passing")
    void nothingRecordedIsNotAPass() {
        TckState state = new TckState();
        EventSteps steps = stepsWith(state);

        assertThatThrownBy(() -> steps.theEventHandlerShouldHaveBeenExecutedWithNoneBefore(READY, ERROR))
                .as("it awaits the event it is about; absence is a failure, not a silent pass")
                .hasMessageContaining("provider event ready");
    }

    @Test
    @DisplayName("awaiting one kind of event does not throw away another kind's record")
    void aPositiveAwaitDoesNotEraseTheEvidence() {
        TckState state = new TckState();
        state.events.add(new ProviderEventRecord(ERROR, null));
        state.events.add(new ProviderEventRecord("change", null));
        EventSteps steps = stepsWith(state);

        steps.theEventHandlerShouldHaveBeenExecuted("change");

        assertThat(state.events)
                .as("the error arrived first; claiming the change must not make it unobservable")
                .extracting(ProviderEventRecord::type)
                .containsExactly(ERROR);
    }

    @Test
    @DisplayName("a second await of the same kind waits for a newer record")
    void anAwaitClaimsItsOwnRecord() {
        TckState state = new TckState();
        state.events.add(new ProviderEventRecord(READY, null));
        EventSteps steps = stepsWith(state);

        steps.theEventHandlerShouldHaveBeenExecuted(READY);

        assertThatThrownBy(() -> steps.theEventHandlerShouldHaveBeenExecutedWithin(READY, 150))
                .as("the one record was claimed, so a chain cannot satisfy two awaits with it")
                .hasMessageContaining("provider event ready");
    }
}
