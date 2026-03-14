package dev.openfeature.contrib.tools.flagd.core.e2e;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Cucumber test suite that runs the flagd-api-testkit compliance scenarios
 * against {@link dev.openfeature.contrib.tools.flagd.core.FlagdCore}.
 *
 * <p>Feature files and flag configurations are loaded from the testkit JAR via
 * {@code @SelectClasspathResource("features")} — no local submodule required.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(
        key = GLUE_PROPERTY_NAME,
        value = "dev.openfeature.contrib.tools.flagd.api.testkit,"
                + "dev.openfeature.contrib.tools.flagd.core.e2e")
@ConfigurationParameter(
        key = OBJECT_FACTORY_PROPERTY_NAME,
        value = "io.cucumber.picocontainer.PicoFactory")
public class FlagdCoreEvaluatorTest {}
