package dev.openfeature.contrib.tools.tck;

import dev.openfeature.contrib.tools.tck.internal.TckSuiteListener;
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
 * <p>A run that asks fewer questions than the canonical set and reports success is indistinguishable
 * from a full one. Shrinking happens through a feature file dropped into {@code gherkin/}, a {@code
 * cucumber.filter.tags} or {@code cucumber.filter.name} expression, or selectors or glue overridden
 * in {@code junit-platform.properties}.
 *
 * <p>Run by {@link ProviderTckTest} as an ordinary Jupiter test, because the JUnit Platform swallows
 * whatever a {@link org.junit.platform.launcher.TestExecutionListener} throws. It checks the setup
 * rather than counting afterwards, from two kinds of evidence available before any scenario runs:
 * the discovered test plan (captured by {@link TckSuiteListener}), and the effective filter
 * configuration. Cucumber applies the tag filter as a skip at execution, so a filtered scenario is
 * still in the plan and the configured expression has to be read directly.
 *
 * <p>Extension scenarios are ignored: the check is defined over {@code gherkin/} only.
 */
@ExtendWith(CanonicalScenarioGuard.CaptureConfiguration.class)
public final class CanonicalScenarioGuard {

    /** System property that downgrades this check to a skip, so a deliberately partial run says so. */
    public static final String PARTIAL_PROPERTY = "tck.partial";

    /** Environment variable equivalent of {@link #PARTIAL_PROPERTY}. */
    public static final String PARTIAL_ENV = "TCK_PARTIAL";

    /** Cucumber configuration keys that stop a discovered scenario from running. */
    private static final String[] FILTER_KEYS = {
        Constants.FILTER_TAGS_PROPERTY_NAME, Constants.FILTER_NAME_PROPERTY_NAME
    };

    /**
     * What each TCK suite selected under {@code gherkin/}, keyed by suite class name. Accumulated,
     * because a suite discovers its children through a launcher of its own and so this is called more
     * than once per build.
     */
    private static final Map<String, Set<CanonicalScenarios.Ref>> DISCOVERED = new ConcurrentHashMap<>();

    private static final String CANONICAL_PREFIX = ProviderTck.FEATURES + "/";

    /**
     * Records what the suites in a discovered plan will run. Public only for the listener in the
     * internal package.
     *
     * @param plan the plan about to be executed
     */
    public static void observe(TestPlan plan) {
        for (TestIdentifier root : plan.getRoots()) {
            collectSuites(plan, root);
        }
    }

    /** Clears the recorded plans, so a test can drive the guard over its own. */
    static void forget() {
        DISCOVERED.clear();
    }

    /**
     * Returns the canonical scenarios each observed suite selected.
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
     * @param discovered what each suite selected under {@code gherkin/}
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
     * Returns the canonical scenario a test identifier stands for, if it is one. For a Scenario
     * Outline the position is the {@code Examples} row's line, so each row counts separately.
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
     * Captures the extension context, because Jupiter resolves configuration parameters for an
     * extension and not for a test method. Inside a suite it sees the suite's own
     * {@code @ConfigurationParameter} values.
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
