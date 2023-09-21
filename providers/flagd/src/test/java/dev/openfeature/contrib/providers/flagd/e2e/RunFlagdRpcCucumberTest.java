package dev.openfeature.contrib.providers.flagd.e2e;

import org.apache.logging.log4j.core.config.Order;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

@Order(value = Integer.MAX_VALUE)
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/evaluation.feature")
@SelectClasspathResource("features/flagd-json-evaluator.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "dev.openfeature.contrib.providers.flagd.e2e.rpc,dev.openfeature.contrib.providers.flagd.e2e.steps")
public class RunFlagdRpcCucumberTest {
  
}

