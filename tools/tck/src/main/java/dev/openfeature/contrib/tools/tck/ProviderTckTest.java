package dev.openfeature.contrib.tools.tck;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Base JUnit Platform Suite for the Provider TCK, carrying all Cucumber configuration.
 *
 * <p>Extend directly for a provider with no backend (with e.g. {@link BackendControl#inProcess()});
 * otherwise extend {@link ContainerizedProviderTckTest}. Scenarios run serially.
 *
 * <p>Extra scenarios go in {@code src/test/resources/extensions/} with steps in package
 * {@code openfeature.tck.extensions}.
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
