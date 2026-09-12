package dev.openfeature.contrib.tools.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.junit.platform.engine.Constants;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * A run that quietly asks fewer questions must not be able to report success.
 *
 * <p>Two levels here, deliberately. The comparison itself is checked directly, because a failure
 * message is the whole product of a guard and it has to name the scenario that went missing. The
 * wiring is checked by running the guard through the JUnit Platform under a real filter
 * configuration, because "the guard sees what the Cucumber engine beside it sees" is a claim about
 * the platform rather than about this code.
 *
 * <p>No scenario is executed anywhere in this file. The guard works from the discovered plan and the
 * run's configuration, which is what lets it be checked — and, in a real build, fail — without a
 * backend.
 */
class CanonicalScenarioGuardTest {

    private static final Map<String, String> UNFILTERED = Collections.emptyMap();

    @BeforeEach
    void forgetPreviousPlans() {
        CanonicalScenarioGuard.forget();
    }

    @Test
    @DisplayName("the canonical set is read from this artifact, outline row by outline row")
    void theCanonicalSetIsReadFromTheArtifact() {
        Set<CanonicalScenarios.Ref> canonical = CanonicalScenarios.shipped();

        assertThat(canonical).isNotEmpty();
        assertThat(canonical)
                .as("every canonical scenario comes from a packaged feature file")
                .allSatisfy(ref -> assertThat(ref.toString()).startsWith(ProviderTck.FEATURES + "/"));

        // The eleven rows of the type-mismatch matrix share one name, so a set that counted scenarios
        // by name would see one of them. Each Examples row is its own line and its own entry.
        assertThat(canonical)
                .filteredOn(ref -> ref.toString().contains("Requesting the wrong type returns the code default"))
                .hasSize(11);
    }

    @Test
    @DisplayName("a suite that selects the whole canonical set, unfiltered, passes")
    void anIntactSuitePasses() {
        CanonicalScenarioGuard.observe(discoverFixtureSuite());

        assertThat(CanonicalScenarioGuard.check(
                        CanonicalScenarios.shipped(), CanonicalScenarioGuard.discovered(), UNFILTERED))
                .isNull();
    }

    @Test
    @DisplayName("extension scenarios neither count towards the canonical set nor disturb it")
    void extensionScenariosAreIgnored() {
        CanonicalScenarioGuard.observe(discoverFixtureSuite());

        Set<CanonicalScenarios.Ref> observed =
                CanonicalScenarioGuard.discovered().get(TckSuiteFixture.class.getName());

        // The fixture suite does select an extension feature — ExtensionPointTest checks that it does
        // — and none of it reaches the guard.
        assertThat(observed)
                .as("the guard is defined over %s/ alone", ProviderTck.FEATURES)
                .isEqualTo(CanonicalScenarios.shipped())
                .allSatisfy(ref -> assertThat(ref.toString()).doesNotContain(ProviderTck.EXTENSIONS + "/"));
    }

    @Test
    @DisplayName("a scenario filter fails the run, whatever it would have excluded")
    void aScenarioFilterFails() {
        CanonicalScenarioGuard.observe(discoverFixtureSuite());

        String problems = CanonicalScenarioGuard.check(
                CanonicalScenarios.shipped(),
                CanonicalScenarioGuard.discovered(),
                Collections.singletonMap(Constants.FILTER_TAGS_PROPERTY_NAME, "not @events"));

        // Cucumber applies a tag filter by skipping scenarios during execution, so the plan still
        // contains them and only the configured expression shows what will be left out. The guard
        // therefore rejects the filter itself rather than trying to predict what it matches.
        assertThat(problems)
                .isNotNull()
                .contains(Constants.FILTER_TAGS_PROPERTY_NAME)
                .contains("not @events")
                .as("and it points at the mechanism that legitimately narrows a run")
                .contains("capabilities()");
    }

    @Test
    @DisplayName("the guard reads the filter the Cucumber engine beside it would read")
    void theGuardReadsTheRunsFilterConfiguration() {
        CanonicalScenarioGuard.observe(discoverFixtureSuite());

        TestExecutionSummary filtered = runGuard(Constants.FILTER_TAGS_PROPERTY_NAME, "@events");

        assertThat(filtered.getTestsFailedCount())
                .as("a filtered run fails at the guard, before a Compose stack is worth starting")
                .isEqualTo(1);
        assertThat(filtered.getFailures().get(0).getException())
                .hasMessageContaining(Constants.FILTER_TAGS_PROPERTY_NAME);
    }

    @Test
    @DisplayName("an unfiltered run of an intact suite passes the guard as executed")
    void anIntactRunPassesTheGuard() {
        CanonicalScenarioGuard.observe(discoverFixtureSuite());

        assertThat(runGuard(null, null).getTestsSucceededCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a run that declares itself partial is skipped rather than passed")
    void aPartialRunIsSkipped() {
        CanonicalScenarioGuard.observe(discoverFixtureSuite());

        String previous = System.getProperty(CanonicalScenarioGuard.PARTIAL_PROPERTY);
        System.setProperty(CanonicalScenarioGuard.PARTIAL_PROPERTY, "true");
        try {
            TestExecutionSummary summary = runGuard(Constants.FILTER_TAGS_PROPERTY_NAME, "@events");

            // Aborted rather than skipped: the test ran and declined to conclude, which is what an
            // assumption produces and what Surefire and the JUnit reports show as skipped.
            assertThat(summary.getTestsAbortedCount())
                    .as("a skip says the canonical set was not verified; a pass would claim it was")
                    .isEqualTo(1);
            assertThat(summary.getTestsSucceededCount()).isZero();
            assertThat(summary.getTestsFailedCount()).isZero();
        } finally {
            restore(CanonicalScenarioGuard.PARTIAL_PROPERTY, previous);
        }
    }

    @Test
    @DisplayName("a feature file added to the canonical directory fails the run")
    void anAddedCanonicalFeatureFails() {
        Set<CanonicalScenarios.Ref> withExtra = new LinkedHashSet<>(CanonicalScenarios.shipped());
        withExtra.add(new CanonicalScenarios.Ref(ProviderTck.FEATURES + "/vendor.feature", 7, "A vendor scenario"));

        String problems = CanonicalScenarioGuard.check(
                CanonicalScenarios.shipped(),
                Collections.singletonMap(TckSuiteFixture.class.getName(), withExtra),
                UNFILTERED);

        assertThat(problems)
                .isNotNull()
                .contains(ProviderTck.FEATURES + "/vendor.feature")
                .as("and it says where the scenario should have gone")
                .contains(ProviderTck.EXTENSIONS + "/");
    }

    @Test
    @DisplayName("a canonical file replaced by another of the same name fails the run")
    void aShadowedCanonicalFileFails() {
        // What the separate extension directory exists to prevent, expressed as what the guard sees
        // if it happens anyway: the replacement compiles to different scenarios, so entries of the
        // canonical set go missing.
        Set<CanonicalScenarios.Ref> shadowed = new LinkedHashSet<>(CanonicalScenarios.shipped());
        Iterator<CanonicalScenarios.Ref> entries = shadowed.iterator();
        CanonicalScenarios.Ref removed = entries.next();
        entries.remove();

        String problems = CanonicalScenarioGuard.check(
                CanonicalScenarios.shipped(),
                Collections.singletonMap(TckSuiteFixture.class.getName(), shadowed),
                UNFILTERED);

        assertThat(problems).isNotNull().contains(removed.toString()).contains("shadowing");
    }

    @Test
    @DisplayName("a plan with no TCK suite in it is a failure, not a pass")
    void anUnobservedRunFails() {
        Map<String, Set<CanonicalScenarios.Ref>> nothing = Collections.emptyMap();

        assertThat(CanonicalScenarioGuard.check(CanonicalScenarios.shipped(), nothing, UNFILTERED))
                .as("an unverifiable conformance run is not a conformance run")
                .isNotNull()
                .contains("TckSuiteListener")
                .contains(CanonicalScenarioGuard.PARTIAL_PROPERTY);
    }

    @Test
    @DisplayName("the partial-run escape hatch carries no PROVIDER_ prefix either")
    void thePartialKnobIsSpelledWithoutThePrefix() {
        // Literals on purpose: every TCK knob an adopter or a CI job sets is spelled TCK_*, and one
        // of them keeping the old PROVIDER_TCK_* prefix is the half-rename that makes the set
        // unguessable.
        assertThat(CanonicalScenarioGuard.PARTIAL_ENV).isEqualTo("TCK_PARTIAL");
        assertThat(CanonicalScenarioGuard.PARTIAL_PROPERTY).isEqualTo("tck.partial");
    }

    /** Discovers the real suite configuration, without executing anything. */
    private static org.junit.platform.launcher.TestPlan discoverFixtureSuite() {
        return LauncherFactory.create()
                .discover(LauncherDiscoveryRequestBuilder.request()
                        .selectors(DiscoverySelectors.selectClass(TckSuiteFixture.class))
                        .build());
    }

    /** Runs the guard itself through the JUnit Platform, optionally under a Cucumber filter. */
    private static TestExecutionSummary runGuard(String key, String value) {
        LauncherDiscoveryRequestBuilder request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(CanonicalScenarioGuard.class))
                .filters(EngineFilter.includeEngines("junit-jupiter"));
        if (key != null) {
            request.configurationParameter(key, value);
        }

        SummaryGeneratingListener summary = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request.build(), summary);
        return summary.getSummary();
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }
}
