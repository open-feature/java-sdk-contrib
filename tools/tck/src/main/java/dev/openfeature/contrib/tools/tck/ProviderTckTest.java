package dev.openfeature.contrib.tools.tck;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Base JUnit Platform Suite for the OpenFeature Provider TCK.
 *
 * <p>Carries all Cucumber runner configuration so that a provider author writes no test
 * infrastructure at all. The canonical feature files are packaged inside this JAR and selected from
 * the classpath, so consumers need no git submodule of their own.
 *
 * <h2>Which base class to extend</h2>
 *
 * <p>This one is lifecycle-agnostic: it starts nothing and knows nothing about how the backend is
 * reached. Extend it directly when your provider has <strong>no backend</strong> — an in-memory,
 * environment-variable or file-based provider — and supply an in-process {@link BackendControl}
 * such as {@link InProcessBackendControl}.
 *
 * <p>When your provider talks to an external backend, extend {@link ContainerizedProviderTckTest}
 * instead. In-process control is for backend-less providers only — see {@link BackendControl}.
 *
 * <h2>Serial execution</h2>
 *
 * <p>Scenarios run <strong>serially</strong>, and this class enforces that rather than merely asking
 * for it: it pins {@code cucumber.execution.parallel.enabled=false} here, where it overrides any
 * {@code junit-platform.properties} the consuming module ships. Several providers already enable
 * Cucumber parallelism for their own suites, and inheriting that setting silently breaks the TCK —
 * backend state is global to the suite, so the failure looks like a flaky provider.
 *
 * <p>This class carries no lifecycle code of its own. Provider registration, event awaiting and
 * backend manipulation are owned by the step definitions in
 * {@code dev.openfeature.contrib.tools.tck.steps}, which reach the harness and its
 * {@link BackendControl} through {@link TckRuntime}.
 *
 * <h2>Adding your own scenarios</h2>
 *
 * <p>A provider with features of its own — flagd's {@code fractional} targeting, a vendor's
 * proprietary evaluation mode — puts feature files in {@code src/test/resources/extensions/} and
 * step definitions in the package {@code openfeature.tck.extensions}, and writes no annotations.
 * Both are selected here, so the extra scenarios run inside this suite: same backend lifecycle, same
 * {@link BackendControl}. The alternative, a second suite of one's own, is a second backend
 * lifecycle to start and a second set of runner configuration to keep in step with this one.
 *
 * <p>The extension directory must not be {@code gherkin/} nor a subdirectory of it — see
 * {@link ProviderTck#EXTENSIONS} for the classpath collision that rules out. It is shipped inside
 * this JAR holding nothing but a README, because {@link SelectClasspathResource} on a resource that
 * exists on no classpath root is a discovery error rather than an empty selection.
 *
 * <p>One JUnit Jupiter test runs alongside the scenarios: {@link CanonicalScenarioGuard}, which
 * fails a suite whose canonical set has been reduced — by a tag filter, a selector override, or a
 * feature file shadowing a canonical one. It is why {@code junit-jupiter} is in the engine list. It
 * inspects the discovered test plan and the run's filter configuration, both of which are settled
 * before the first scenario, so it costs nothing and does not depend on the order the engines happen
 * to run in.
 *
 * <p>Every value these annotations carry is named in {@link ProviderTck}. An adopter who does write a
 * {@code @ConfigurationParameter} of their own composes from those constants —
 * {@code ProviderTck.ALL_GLUE + ",com.vendor.steps"} — rather than restating this configuration as a
 * string literal that nothing would keep in step.
 *
 * @see ProviderTckHarness
 * @see ContainerizedProviderTckTest
 * @see ProviderTck
 * @see ConformanceReportPlugin
 * @see CanonicalScenarioGuard
 */
@Suite
@IncludeEngines({"cucumber", "junit-jupiter"})
@SelectClasspathResource(ProviderTck.FEATURES)
@SelectClasspathResource(ProviderTck.EXTENSIONS)
@SelectClasses(CanonicalScenarioGuard.class)
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME, value = ProviderTck.PLUGINS)
@ConfigurationParameter(
        key = Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME,
        value = ProviderTck.PARALLEL_EXECUTION_ENABLED)
@ConfigurationParameter(
        key = Constants.EXECUTION_MODE_FEATURE_PROPERTY_NAME,
        value = ProviderTck.FEATURE_EXECUTION_MODE)
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = ProviderTck.ALL_GLUE)
@ConfigurationParameter(key = Constants.OBJECT_FACTORY_PROPERTY_NAME, value = ProviderTck.OBJECT_FACTORY)
public abstract class ProviderTckTest implements ProviderTckHarness {}
