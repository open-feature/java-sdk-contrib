package dev.openfeature.contrib.providers.flagd.e2e;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.jupiter.api.Order;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectFile;
import org.junit.platform.suite.api.Suite;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Class for running the tests associated with "stable" e2e tests (no fake disconnection) for the
 * in-process provider
 */
@Order(value = Integer.MAX_VALUE)
@Suite
@IncludeEngines("cucumber")
@SelectFile("spec/specification/assets/gherkin/evaluation.feature")
@SelectFile("test-harness/gherkin/flagd-json-evaluator.feature")
@SelectFile("test-harness/gherkin/flagd.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(
        key = GLUE_PROPERTY_NAME,
        value =
                "dev.openfeature.contrib.providers.flagd.e2e.process.envoy,dev.openfeature.contrib.providers.flagd.e2e.steps")
@Testcontainers
public class RunFlagdInProcessEnvoyCucumberTest {}
