package dev.openfeature.contrib.providers.flagd.e2e;

import org.junit.jupiter.api.Order;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Class for running the tests associated with "stable" e2e tests (no fake disconnection) for the in-process provider
 */
@Order(value = Integer.MAX_VALUE)
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/evaluation.feature")
@SelectClasspathResource("features/flagd-json-evaluator.feature")
@SelectClasspathResource("features/flagd.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "dev.openfeature.contrib.providers.flagd.e2e.process.envoy,dev.openfeature.contrib.providers.flagd.e2e.steps")
@Testcontainers
public class RunFlagdInProcessEnvoyCucumberTest {

}
