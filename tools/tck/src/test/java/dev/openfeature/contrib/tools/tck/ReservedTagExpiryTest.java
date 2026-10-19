package dev.openfeature.contrib.tools.tck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.opentest4j.TestAbortedException;

/**
 * A scenario carrying a {@linkplain Capability#reserved() reserved} tag fails the run rather than
 * being silently skipped.
 */
class ReservedTagExpiryTest {

    @Test
    @DisplayName("a reserved tag fails its scenario, and a comment naming one does not")
    void aReservedTagFailsTheRun() {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create()
                .execute(
                        LauncherDiscoveryRequestBuilder.request()
                                .selectors(DiscoverySelectors.selectClass(ReservedTagSuiteFixture.class))
                                .build(),
                        listener);
        TestExecutionSummary summary = listener.getSummary();

        assertThat(summary.getTestsFailedCount())
                .as("the scenario tagged %s fails the run rather than being quietly skipped", Capability.CACHING.tag())
                .isEqualTo(1);
        assertThat(summary.getTestsAbortedCount()).as("a failure, not an abort").isZero();
        assertThat(summary.getTestsSkippedCount()).isZero();

        TestExecutionSummary.Failure failure = summary.getFailures().get(0);
        assertThat(failure.getException())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Capability.CACHING.name())
                .hasMessageContaining(Capability.CACHING.tag());
        assertThat(failure.getTestIdentifier().getDisplayName())
                .as("the tagged scenario is the one that failed")
                .contains("still calls reserved");

        assertThat(summary.getTestsSucceededCount())
                .as("the untagged scenario passes, though a Gherkin comment in it names %s", Capability.CACHING.tag())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the reserved tag is reported even when another tag on the scenario is undeclared")
    void theExpiryIsReportedBeforeTheSkip() {
        assertThatThrownBy(() -> Capability.requireDeclared(
                        Arrays.asList(Capability.EVENTS.tag(), Capability.CACHING.tag()),
                        EnumSet.noneOf(Capability.class)))
                .as("the expired reservation is reported before the undeclared skip")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Capability.CACHING.tag());
    }

    @Test
    @DisplayName("a tag that gates nothing, and a declared capability, still pass the gate")
    void ordinaryTagsAreUnaffected() {
        assertThatCode(() -> Capability.requireDeclared(
                        Arrays.asList("@some-adopter-tag", Capability.EVENTS.tag()), EnumSet.of(Capability.EVENTS)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> Capability.requireDeclared(
                        Collections.singletonList(Capability.EVENTS.tag()), EnumSet.noneOf(Capability.class)))
                .as("an undeclared capability is still a skip, not a failure")
                .isInstanceOf(TestAbortedException.class);
    }
}
