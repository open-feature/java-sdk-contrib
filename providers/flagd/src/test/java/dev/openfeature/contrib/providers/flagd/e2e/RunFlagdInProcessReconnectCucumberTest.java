package dev.openfeature.contrib.providers.flagd.e2e;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.jupiter.api.Order;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Class for running the reconnection tests for the in-process provider */
@Order(value = Integer.MAX_VALUE)
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/flagd-reconnect.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(
        key = GLUE_PROPERTY_NAME,
        value =
                "dev.openfeature.contrib.providers.flagd.e2e.reconnect.process,dev.openfeature.contrib.providers.flagd.e2e.reconnect.steps")
@Testcontainers
public class RunFlagdInProcessReconnectCucumberTest {}
