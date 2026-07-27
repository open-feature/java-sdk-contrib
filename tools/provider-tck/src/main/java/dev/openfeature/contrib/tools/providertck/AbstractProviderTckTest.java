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
 * @see ProviderTckHarness
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME, value = "summary")
@ConfigurationParameter(key = Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME, value = "false")
@ConfigurationParameter(key = Constants.EXECUTION_MODE_FEATURE_PROPERTY_NAME, value = "same_thread")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "dev.openfeature.contrib.tools.providertck.steps")
@ConfigurationParameter(key = Constants.OBJECT_FACTORY_PROPERTY_NAME, value = "io.cucumber.picocontainer.PicoFactory")
public abstract class AbstractProviderTckTest implements ProviderTckHarness {}
