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
 * A scenario carrying a {@linkplain Capability#reserved() reserved} tag fails the run.
 *
 * <p>This is the expiry check on the reserved list, and it is here because the failure it catches is
 * silent in both directions. A reserved capability cannot be declared, so the day the specification
 * adds the first scenario for one, every adopter's run reports that scenario as skipped for a
 * capability they are not permitted to claim. The report is well-formed and the suite is green, so
 * the new scenario is executed by nobody — the unclaimable-capability failure of Appendix F. Nothing
 * else in this module notices it: the scenario was collected and gated rather than dropped, and a
 * capability-gated skip is explicitly not a gap.
 *
 * <p>The first test runs {@link ReservedTagSuiteFixture}, a real suite over two real scenarios, and
 * looks at the outcome each one got. Running it rather than calling {@link CapabilityGate} directly
 * is what makes it a test of the rule as an adopter meets it: the tags are Cucumber's own parse, the
 * hook is the one the suite installs, and the failure has to survive into the JUnit results the same
 * way the skip does.
 *
 * <p>It is also written so that <em>removing</em> the check does not merely change an error message.
 * With the check gone, the tagged scenario is skipped for an undeclared capability instead — one
 * fewer failure, one more abort — which is exactly the silent outcome the check exists to prevent,
 * and both counts are asserted.
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
        assertThat(summary.getTestsAbortedCount())
                .as("and it is a failure, not an abort — an abort is the skip this check exists to "
                        + "prevent, and is what remains if the check is removed")
                .isZero();
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
                .as(
                        "the untagged scenario passes, though a Gherkin comment in it names %s — "
                                + "gherkin/events.feature carries exactly such a comment, so a check that "
                                + "scanned the feature files as text would fail every adoption",
                        Capability.CACHING.tag())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the reserved tag is reported even when another tag on the scenario is undeclared")
    void theExpiryIsReportedBeforeTheSkip() {
        assertThatThrownBy(() -> CapabilityGate.requireDeclared(
                        Arrays.asList(Capability.EVENTS.tag(), Capability.CACHING.tag()),
                        EnumSet.noneOf(Capability.class)))
                .as("both tags are undeclared, and the expired reservation is the one worth saying — "
                        + "gating tag by tag would abort on @events and never reach @caching")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Capability.CACHING.tag());
    }

    @Test
    @DisplayName("a tag that gates nothing, and a declared capability, still pass the gate")
    void ordinaryTagsAreUnaffected() {
        assertThatCode(() -> CapabilityGate.requireDeclared(
                        Arrays.asList("@some-adopter-tag", Capability.EVENTS.tag()), EnumSet.of(Capability.EVENTS)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> CapabilityGate.requireDeclared(
                        Collections.singletonList(Capability.EVENTS.tag()), EnumSet.noneOf(Capability.class)))
                .as("an undeclared capability is still a skip, not a failure")
                .isInstanceOf(TestAbortedException.class);
    }
}
