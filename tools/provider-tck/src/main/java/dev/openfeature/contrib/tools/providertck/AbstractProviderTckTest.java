package dev.openfeature.contrib.tools.providertck;

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
 * <p>To adopt the TCK, extend this class, implement the four abstract methods of
 * {@link ProviderTckHarness}, and register the concrete class in
 * {@code src/test/resources/META-INF/services/dev.openfeature.contrib.tools.providertck.ProviderTckHarness}.
 *
 * <p>Scenarios run <strong>serially</strong>, and this class enforces that rather than merely
 * asking for it. Control API state — which flags are seeded, whether the backend is reachable — is
 * global to the Compose stack, so concurrent scenarios corrupt each other: one scenario's
 * {@code /start} restarts the backend underneath another's disconnect assertion. The failure looks
 * like a flaky provider rather than a broken test, which makes it expensive to diagnose.
 *
 * <p>The suite therefore pins {@code cucumber.execution.parallel.enabled=false} here, where it
 * overrides any {@code junit-platform.properties} the consuming module happens to ship. Several
 * providers already enable Cucumber parallelism for their own suites, and inheriting that setting
 * silently breaks the TCK.
 *
 * <p>Note this class carries no lifecycle code. The Compose stack, the control API client, provider
 * registration and event awaiting are all owned by the step definitions in
 * {@code dev.openfeature.contrib.tools.providertck.steps}, which reach the harness through
 * {@link TckRuntime}.
 *
 * <p>The suite also carries {@link ConformanceReportPlugin}, so an adopter needs no configuration to
 * publish a machine-readable conformance report: setting {@code PROVIDER_TCK_REPORT_DIR} on a run is
 * enough, and leaving it unset writes nothing.
 *
 * <p>That plugin is registered here rather than as Cucumber's built-in {@code message:<path>} plugin
 * for one reason: a {@code @ConfigurationParameter} value is a compile-time constant, so the
 * built-in plugin's output path cannot be derived from the report directory the run asked for, and
 * two suites in one module — flagd's two resolvers — would write to the same file. The plugin
 * delegates to Cucumber's own message formatter for the stream itself, so the results are the same
 * bytes {@code message:<path>} would have produced, at a path this suite can choose.
 *
 * <p><strong>Adding your own scenarios.</strong> A provider with features of its own — flagd's
 * {@code fractional} targeting, a vendor's proprietary evaluation mode — puts feature files in
 * {@code src/test/resources/tck-extensions/} and step definitions in the package
 * {@code openfeature.tck.extensions}, and writes no annotations. Both are selected here, so the
 * extra scenarios run inside this suite: same Compose stack, same {@code @BeforeAll}, same control
 * API, same conformance report. The alternative — a second suite of one's own — is a second backend
 * lifecycle to start and a second set of runner configuration to keep in step with this one.
 *
 * <p>The extension directory is <em>not</em> {@code features/} and is not a subdirectory of it, for
 * a measured reason. Two classpath roots that contain the same directory are scanned additively, but
 * two that contain the same directory <em>and</em> the same file name are not: one wins silently and
 * the other file is never read. An adopter who put {@code features/errors.feature} in their test
 * resources would replace a canonical feature with their own and see the suite pass — a conformance
 * suite reporting success for questions it never asked. A distinct directory name removes the
 * collision rather than documenting it.
 *
 * <p>The directory is shipped inside this JAR holding nothing but a README, because
 * {@link SelectClasspathResource} on a resource that exists on no classpath root is a discovery
 * error, not an empty selection. An adopter who adds nothing therefore still resolves it. The
 * extension glue package costs nothing when unused either: Cucumber tolerates a glue package that
 * does not exist.
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
public abstract class AbstractProviderTckTest implements ProviderTckHarness {}
