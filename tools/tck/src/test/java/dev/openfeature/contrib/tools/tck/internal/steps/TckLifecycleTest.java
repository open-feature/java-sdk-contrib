package dev.openfeature.contrib.tools.tck.internal.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.openfeature.contrib.tools.tck.BackendControl;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.NoOpProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a lifecycle per object factory buys: one backend per owner rather than one shared.
 *
 * <p>Cucumber creates a factory per thread when scenarios run in parallel, so these are the
 * properties that decide whether enabling it gives each thread its own stack or a race.
 */
class TckLifecycleTest {

    /** A harness that records its own lifecycle and brings nothing up. */
    private static final class RecordingHarness implements ProviderTckHarness {

        private final BackendControl control = BackendControl.inProcess();
        private int starts;
        private int stops;

        @Override
        public FeatureProvider createProvider() {
            return new NoOpProvider();
        }

        @Override
        public BackendControl backendControl() {
            return control;
        }

        @Override
        public void startSuite() {
            starts++;
        }

        @Override
        public void stopSuite() {
            stops++;
        }
    }

    @AfterEach
    void releaseWhateverIsLeft() {
        TckLifecycle.endSuite();
    }

    @Test
    @DisplayName("two lifecycles own separate harnesses and separate backends")
    void lifecyclesAreIndependent() {
        RecordingHarness first = new RecordingHarness();
        RecordingHarness second = new RecordingHarness();

        TckLifecycle one = TckLifecycle.start(first);
        TckLifecycle two = TckLifecycle.start(second);

        assertThat(one).isNotSameAs(two);
        assertThat(one.harness()).isSameAs(first);
        assertThat(two.harness()).isSameAs(second);
        assertThat(one.backendControl())
                .as("a shared control is the thing that makes parallel scenarios race")
                .isNotSameAs(two.backendControl());
        assertThat(first.starts).isEqualTo(1);
        assertThat(second.starts).isEqualTo(1);
        assertThat(TckLifecycle.startedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("endSuite stops every lifecycle, not only the one the suite thread started")
    void endSuiteStopsAll() {
        RecordingHarness onThisThread = new RecordingHarness();
        RecordingHarness onAnother = new RecordingHarness();
        TckLifecycle.start(onThisThread);
        TckLifecycle.start(onAnother);

        TckLifecycle.endSuite();

        assertThat(onThisThread.stops).isEqualTo(1);
        assertThat(onAnother.stops)
                .as("@AfterAll runs on one thread, so the registry is what stops the others")
                .isEqualTo(1);
        assertThat(TckLifecycle.startedCount()).isZero();
    }

    @Test
    @DisplayName("a failed start releases what it allocated instead of leaving it registered")
    void aFailedStartLeavesNothingBehind() {
        class NullControlHarness implements ProviderTckHarness {
            private int stops;

            @Override
            public FeatureProvider createProvider() {
                return new NoOpProvider();
            }

            @Override
            public BackendControl backendControl() {
                return null;
            }

            @Override
            public void stopSuite() {
                stops++;
            }
        }
        NullControlHarness harness = new NullControlHarness();

        assertThatThrownBy(() -> TckLifecycle.start(harness))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("backendControl() returned null");

        assertThat(harness.stops).as("a partial start is torn down by the starter").isEqualTo(1);
        assertThat(TckLifecycle.startedCount())
                .as("and nothing half-started is left for endSuite to find")
                .isZero();
    }

    @Test
    @DisplayName("no suite running means no lifecycle, so extension-only glue registers nothing")
    void noSuiteMeansNoLifecycle() {
        assertThat(TckLifecycle.startForExecutingSuite()).isEmpty();
    }
}
