package dev.openfeature.contrib.tools.providertck;

import io.cucumber.junit.platform.engine.Constants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * Fails a suite that is set up to run less than the whole canonical scenario set.
 *
 * <p>Everything else in this module makes the canonical set easy to extend safely. Nothing makes it
 * hard to shrink, and shrinking it is the failure that matters: a run that asks twenty-seven of the
 * twenty-nine questions and reports success is indistinguishable, in every artifact it produces, from
 * one that asked all twenty-nine. The known ways to get there are a feature file dropped into
 * {@code features/}, a {@code cucumber.filter.tags} or {@code cucumber.filter.name} expression, and
 * selectors or glue overridden in a consuming module's {@code junit-platform.properties}.
 *
 * <p>Selected by {@link AbstractProviderTckTest} as an ordinary JUnit Jupiter test, so a reduced set
 * fails the build the way any other failing test does. A {@link
 * org.junit.platform.launcher.TestExecutionListener} cannot do that job: the JUnit Platform catches
 * and logs whatever a listener throws, which is exactly the silent pass being guarded against.
 *
 * <p>Two kinds of evidence, both available before a single scenario has run:
 *
 * <ul>
 *   <li><strong>the discovered test plan</strong>, captured by {@link TckSuiteListener}. Which
 *       scenarios a suite selected is settled at discovery, so a canonical file that was shadowed,
 *       replaced or added to is visible there.
 *   <li><strong>the effective filter configuration</strong>, read through this test's own
 *       {@link ExtensionContext}. Cucumber applies {@code cucumber.filter.tags} as a skip at
 *       execution rather than as a discovery filter — a filtered scenario is in the plan and never
 *       runs — so the plan cannot show it and the configured expression has to be read directly. The
 *       Jupiter engine inside the suite resolves configuration from the same sources the Cucumber
 *       engine does, whether the value came from a system property, a
 *       {@code junit-platform.properties} or an annotation on the suite.
 * </ul>
 *
 * <p>Checking the setup rather than counting afterwards is not a compromise made for convenience. A
 * count is only complete once the last scenario has finished, and the only hook that runs there is a
 * listener, which cannot fail anything. Both kinds of evidence are settled before the first scenario
 * runs, so the check itself takes no measurable time and needs no Compose stack — though where its
 * result appears in a run depends on the order the JUnit Platform happens to execute the two engines
 * in, which is not specified.
 *
 * <p>Extension scenarios are ignored entirely. The check is defined over {@code features/}, so an
 * adopter's {@code tck-extensions/} scenarios can neither stand in for a canonical scenario nor look
 * like a spurious one.
 *
 * <p>Separable from the rest of the extension work by design: it guards a bypass rather than enabling
 * anything, and dropping it leaves the extension point unaffected.
 */
@ExtendWith(CanonicalScenarioGuard.CaptureConfiguration.class)
public final class CanonicalScenarioGuard {

    /**
     * System property that downgrades this check to a skip.
     *
     * <p>An escape hatch is necessary rather than a weakness. Running one scenario with
     * {@code -Dcucumber.filter.tags=@events} is routine while debugging a provider, and a check that
     * made that impossible would be switched off permanently instead of temporarily. It produces a
     * skip rather than a pass, so the run says out loud that its canonical set was not verified.
     */
    public static final String PARTIAL_PROPERTY = "provider.tck.partial";

    /** Environment variable equivalent of {@link #PARTIAL_PROPERTY}. */
    public static final String PARTIAL_ENV = "PROVIDER_TCK_PARTIAL";

    /** Cucumber configuration keys that stop a discovered scenario from running. */
    private static final String[] FILTER_KEYS = {
        Constants.FILTER_TAGS_PROPERTY_NAME, Constants.FILTER_NAME_PROPERTY_NAME
    };

    /**
     * What each TCK suite in a discovered plan selected under {@code features/}.
     *
     * <p>Keyed by suite class name and accumulated rather than replaced. A suite discovers its
     * children through a launcher of its own, so this is called more than once per build with plans
     * of differing scope, and a plan containing no TCK suite says nothing about the ones already
     * recorded.
     */
    private static final Map<String, Set<CanonicalScenarios.Ref>> DISCOVERED = new ConcurrentHashMap<>();

    private static final String CANONICAL_PREFIX = ProviderTck.FEATURES + "/";

    /**
     * Records what the suites in a discovered plan will run.
     *
     * @param plan the plan about to be executed
     */
    static void observe(TestPlan plan) {
        for (TestIdentifier root : plan.getRoots()) {
            collectSuites(plan, root);
        }
    }

    /** Forgets what has been observed, so a test can drive the guard over a plan of its own. */
    static void forget() {
        DISCOVERED.clear();
    }

    /**
     * Returns what each suite in the observed plans selected under {@code features/}.
     *
     * @return canonical scenarios by suite class name
     */
    static Map<String, Set<CanonicalScenarios.Ref>> discovered() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(DISCOVERED));
    }

    /** Asserts that this run will execute the whole canonical scenario set. */
    @Test
    @DisplayName("the canonical scenario set was not reduced")
    void theCanonicalScenarioSetWasNotReduced() {
        if (partialRunAllowed()) {
            Assumptions.abort("Skipped: " + PARTIAL_PROPERTY + " is set, so the canonical scenario set was not "
                    + "verified. This run is not a conformance run.");
        }

        String problems = check(CanonicalScenarios.shipped(), discovered(), CaptureConfiguration.filters());
        if (problems != null) {
            throw new AssertionError(problems);
        }
    }

    /**
     * Reports every way this run falls short of the canonical set.
     *
     * @param canonical the canonical scenario set, as the TCK ships it
     * @param discovered what each suite selected under {@code features/}
     * @param filters configured Cucumber filters, by configuration key
     * @return a report of every problem found, or {@code null} when there is none
     */
    static String check(
            Set<CanonicalScenarios.Ref> canonical,
            Map<String, Set<CanonicalScenarios.Ref>> discovered,
            Map<String, String> filters) {
        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, String> filter : filters.entrySet()) {
            problems.add(filter.getKey() + " is set to '" + filter.getValue()
                    + "'. Cucumber applies it by skipping scenarios that would otherwise have run, so a "
                    + "conformance run cannot be filtered. Decline capabilities your provider does not have "
                    + "through capabilities() instead — those scenarios are reported as skipped with a reason, "
                    + "which a filtered one is not. To filter anyway while debugging, set -D" + PARTIAL_PROPERTY
                    + "=true and accept that the run is not a conformance run.");
        }

        if (discovered.isEmpty()) {
            problems.add("No TCK suite was found in the JUnit test plan, so it cannot be shown that the canonical "
                    + "scenarios will run. This normally means TckSuiteListener was not auto-registered — the same "
                    + "condition that makes harness discovery fall back to ServiceLoader. Enable JUnit Platform "
                    + "listener auto-registration, or set -D" + PARTIAL_PROPERTY + "=true to accept a run whose "
                    + "canonical set is unverified.");
        }

        for (Map.Entry<String, Set<CanonicalScenarios.Ref>> suite : discovered.entrySet()) {
            Set<CanonicalScenarios.Ref> missing = new TreeSet<>(canonical);
            missing.removeAll(suite.getValue());
            if (!missing.isEmpty()) {
                problems.add(suite.getKey() + " left out " + missing.size() + " of " + canonical.size()
                        + " canonical scenarios: " + missing
                        + ". A conformance run executes the canonical set in full. Look for a selector or glue "
                        + "override in junit-platform.properties, a cucumber.features property, or a feature file "
                        + "of your own in " + CANONICAL_PREFIX + " shadowing a canonical one. Scenarios your "
                        + "provider cannot support are declined through capabilities(), which reports them as "
                        + "skipped rather than removing them.");
            }

            Set<CanonicalScenarios.Ref> unexpected = new TreeSet<>(suite.getValue());
            unexpected.removeAll(canonical);
            if (!unexpected.isEmpty()) {
                problems.add(suite.getKey() + " selected " + unexpected.size() + " scenario(s) under "
                        + CANONICAL_PREFIX + " that this TCK does not ship: " + unexpected
                        + ". That directory is the canonical set, and adding to it changes what conformance means. "
                        + "Put your own scenarios in " + ProviderTck.EXTENSIONS + "/ instead, where they run in the "
                        + "same suite and the same backend lifecycle.");
            }
        }

        return problems.isEmpty() ? null : String.join(System.lineSeparator() + System.lineSeparator(), problems);
    }

    /**
     * Returns the Cucumber filters this run is configured with.
     *
     * @param context the executing test's context, which resolves configuration the way the Cucumber
     *     engine beside it does
     * @return configured filters by configuration key, empty when the run is unfiltered
     */
    static Map<String, String> filtersIn(ExtensionContext context) {
        Map<String, String> configured = new LinkedHashMap<>();
        for (String key : FILTER_KEYS) {
            context.getConfigurationParameter(key)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .ifPresent(value -> configured.put(key, value));
        }
        return configured;
    }

    /** Returns whether the run has declared itself partial. */
    private static boolean partialRunAllowed() {
        return isTrue(System.getProperty(PARTIAL_PROPERTY)) || isTrue(System.getenv(PARTIAL_ENV));
    }

    private static boolean isTrue(String value) {
        return value != null && ("".equals(value.trim()) || Boolean.parseBoolean(value.trim()));
    }

    private static void collectSuites(TestPlan plan, TestIdentifier identifier) {
        Optional<Class<? extends ProviderTckHarness>> suite = TckSuiteListener.harnessClassOf(identifier);
        if (suite.isPresent()) {
            Set<CanonicalScenarios.Ref> canonical = new LinkedHashSet<>();
            collectCanonical(plan, identifier, canonical);
            DISCOVERED.put(suite.get().getName(), Collections.unmodifiableSet(canonical));
            return;
        }
        for (TestIdentifier child : plan.getChildren(identifier)) {
            collectSuites(plan, child);
        }
    }

    private static void collectCanonical(TestPlan plan, TestIdentifier identifier, Set<CanonicalScenarios.Ref> into) {
        if (identifier.isTest()) {
            canonicalRefOf(identifier).ifPresent(into::add);
        }
        for (TestIdentifier child : plan.getChildren(identifier)) {
            collectCanonical(plan, child, into);
        }
    }

    /**
     * Returns the canonical scenario a test identifier stands for, if it is one.
     *
     * <p>A Cucumber scenario discovered from a classpath resource carries a
     * {@link ClasspathResourceSource} naming the feature resource and the scenario's line — for a
     * Scenario Outline, the line of the {@code Examples} row it was compiled from, which is what makes
     * each row count separately.
     */
    private static Optional<CanonicalScenarios.Ref> canonicalRefOf(TestIdentifier identifier) {
        Optional<TestSource> source = identifier.getSource();
        if (!source.isPresent() || !(source.get() instanceof ClasspathResourceSource)) {
            return Optional.empty();
        }
        ClasspathResourceSource resource = (ClasspathResourceSource) source.get();
        String name = resource.getClasspathResourceName();
        if (!name.startsWith(CANONICAL_PREFIX)) {
            return Optional.empty();
        }
        return resource.getPosition()
                .map(position -> new CanonicalScenarios.Ref(name, position.getLine(), identifier.getDisplayName()));
    }

    /**
     * Hands the guard the configuration of the engine it is running in.
     *
     * <p>Jupiter resolves configuration parameters for an extension, not for a test method, so the
     * context is captured here rather than injected. Inside a suite it carries the suite's own
     * {@code @ConfigurationParameter} values as well as the system properties and
     * {@code junit-platform.properties} the Cucumber engine beside it reads.
     */
    static final class CaptureConfiguration implements BeforeEachCallback {

        private static volatile Map<String, String> filters = Collections.emptyMap();

        @Override
        public void beforeEach(ExtensionContext context) {
            filters = Collections.unmodifiableMap(filtersIn(context));
        }

        static Map<String, String> filters() {
            return filters;
        }
    }
}
