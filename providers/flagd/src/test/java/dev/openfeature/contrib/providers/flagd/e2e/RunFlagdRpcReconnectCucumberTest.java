package dev.openfeature.contrib.providers.flagd.e2e;

import org.apache.logging.log4j.core.config.Order;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Class for running the reconnection tests for the RPC provider
 */
@Order(value = Integer.MAX_VALUE)
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/flagd-reconnect.feature")
@SelectClasspathResource("features/events.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "dev.openfeature.contrib.providers.flagd.e2e.reconnect.rpc,dev.openfeature.contrib.providers.flagd.e2e.reconnect.steps")
@Testcontainers
public class RunFlagdRpcReconnectCucumberTest {

}

