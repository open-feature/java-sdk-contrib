package dev.openfeature.contrib.providers.flagd.e2e;

import org.apache.logging.log4j.core.config.Order;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectFile;
import org.junit.platform.suite.api.Suite;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Order(value = Integer.MAX_VALUE)
@Suite(failIfNoTests = false)
@IncludeEngines("cucumber")
// Not implemented in flagd
//@SelectFile("spec/specification/assets/gherkin/evaluation.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(
        key = GLUE_PROPERTY_NAME,
        value =
                "dev.openfeature.contrib.providers.flagd.e2e.socket.process,dev.openfeature.contrib.providers.flagd.e2e.steps")
@Testcontainers
@EnabledOnOs(OS.LINUX)
public class RunFlagdInProcessSocketCucumberTest {}
