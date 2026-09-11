package dev.openfeature.contrib.tools.providertck;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
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
 * instead. It adds the Compose stack lifecycle, port discovery and {@link HttpBackendControl}, and
 * the HTTP control API in {@code openapi/control-api.yaml} remains the normative contract for that
 * conformance claim. In-process control is for backend-less providers only; an external backend
 * driven through a custom in-JVM {@code BackendControl} bypasses that contract and proves nothing.
 *
 * <h2>Serial execution</h2>
 *
 * <p>Scenarios run <strong>serially</strong>, and this class enforces that rather than merely
 * asking for it. Backend state — which flags are seeded, whether the backend is reachable — is
 * global to the suite, so concurrent scenarios corrupt each other: one scenario's reconnect
 * restarts the backend underneath another's disconnect assertion. The failure looks like a flaky
 * provider rather than a broken test, which makes it expensive to diagnose.
 *
 * <p>The suite therefore pins {@code cucumber.execution.parallel.enabled=false} here, where it
 * overrides any {@code junit-platform.properties} the consuming module happens to ship. Several
 * providers already enable Cucumber parallelism for their own suites, and inheriting that setting
 * silently breaks the TCK.
 *
 * <p>Note this class carries no lifecycle code of its own. Provider registration, event awaiting
 * and backend manipulation are owned by the step definitions in
 * {@code dev.openfeature.contrib.tools.providertck.steps}, which reach the harness and its
 * {@link BackendControl} through {@link TckRuntime}.
 *
 * <h2>Adding your own scenarios</h2>
 *
 * <p>A provider with features of its own — flagd's {@code fractional} targeting, a vendor's
 * proprietary evaluation mode — puts feature files in {@code src/test/resources/extensions/} and
 * step definitions in the package {@code openfeature.tck.extensions}, and writes no annotations.
 * Both are selected here, so the extra scenarios run inside this suite: same backend lifecycle, same
 * {@code @BeforeAll}, same {@link BackendControl}. The alternative — a second suite of one's own —
 * is a second backend lifecycle to start and a second set of runner configuration to keep in step
 * with this one.
 *
 * <p>The extension directory is <em>not</em> {@code gherkin/} and is not a subdirectory of it, for
 * a measured reason. Two classpath roots that contain the same directory are scanned additively, but
 * two that contain the same directory <em>and</em> the same file name are not: one wins silently and
 * the other file is never read. An adopter who put {@code gherkin/errors.feature} in their test
 * resources would replace a canonical feature with their own and see the suite pass — a conformance
 * suite reporting success for questions it never asked. {@code gherkin/} and {@code extensions/}
 * being two distinct directories removes the collision rather than documenting it.
 *
 * <p>Both names come from Appendix F, which identifies a canonical feature by its path relative to
 * the asset directory and reserves {@code extensions/} for an adopter's own. That is what makes the
 * URIs this suite reports — {@code classpath:gherkin/errors.feature},
 * {@code classpath:extensions/fractional.feature} — partition the same way in every language.
 *
 * <p>The directory is shipped inside this JAR holding nothing but a README, because
 * {@link SelectClasspathResource} on a resource that exists on no classpath root is a discovery
 * error, not an empty selection. An adopter who adds nothing therefore still resolves it. The
 * extension glue package costs nothing when unused either: Cucumber tolerates a glue package that
 * does not exist.
 *
 * <p>Every value these annotations carry is named in {@link ProviderTck}. An adopter who does write a
 * {@code @ConfigurationParameter} of their own composes from those constants —
 * {@code ProviderTck.ALL_GLUE + ",com.vendor.steps"} — rather than restating this configuration as a
 * string literal that nothing would keep in step.
 *
 * @see ProviderTckHarness
 * @see ContainerizedProviderTckTest
 * @see ProviderTck
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource(ProviderTck.FEATURES)
@SelectClasspathResource(ProviderTck.EXTENSIONS)
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
