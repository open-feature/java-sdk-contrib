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
 * @see ProviderTckHarness
 * @see ContainerizedProviderTckTest
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME, value = "summary")
@ConfigurationParameter(key = Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME, value = "false")
@ConfigurationParameter(key = Constants.EXECUTION_MODE_FEATURE_PROPERTY_NAME, value = "same_thread")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "dev.openfeature.contrib.tools.providertck.steps")
@ConfigurationParameter(key = Constants.OBJECT_FACTORY_PROPERTY_NAME, value = "io.cucumber.picocontainer.PicoFactory")
public abstract class ProviderTckTest implements ProviderTckHarness {}
