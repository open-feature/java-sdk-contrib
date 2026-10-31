package dev.openfeature.contrib.providers.flagd.e2e;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PARALLEL_CONFIG_DYNAMIC_FACTOR_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PARALLEL_CONFIG_STRATEGY_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import dev.openfeature.contrib.providers.flagd.Config;
import org.apache.logging.log4j.core.config.Order;
import org.junit.platform.suite.api.BeforeSuite;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Class for running the reconnection tests for the RPC provider
 */
@Order(value = Integer.MAX_VALUE)
@Suite
@IncludeEngines("cucumber")
@SelectDirectories("test-harness/gherkin")
// if you want to run just one feature file, use the following line instead of @SelectDirectories
// @SelectFile("test-harness/gherkin/selector.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "dev.openfeature.contrib.providers.flagd.e2e.steps")
@ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME, value = "io.cucumber.picocontainer.PicoFactory")
@IncludeTags("in-process")
@ExcludeTags({"unixsocket", "fractional-v1", "fractional-v2", "deprecated"})
// Declared here rather than in a module-wide junit-platform.properties, which also reached the
// Provider TCK suites and left them unable to turn parallelism back on.
@ConfigurationParameter(key = PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME, value = "true")
@ConfigurationParameter(key = PARALLEL_CONFIG_STRATEGY_PROPERTY_NAME, value = "dynamic")
@ConfigurationParameter(key = PARALLEL_CONFIG_DYNAMIC_FACTOR_PROPERTY_NAME, value = "1")
// Concurrent container restarts can outlast the reconnection timeout.
@ConfigurationParameter(key = "cucumber.execution.exclusive-resources.grace.read-write", value = "CONTAINER_RESTART")
// @env-var scenarios mutate process-wide variables that every config scenario reads.
@ConfigurationParameter(
        key = "cucumber.execution.exclusive-resources.env-var.read-write",
        value = "org.junit.platform.engine.support.hierarchical.ExclusiveResource.GLOBAL_KEY")
@Testcontainers
public class RunInProcessTest {

    @BeforeSuite
    public static void before() {
        State.resolverType = Config.Resolver.IN_PROCESS;
    }
}
