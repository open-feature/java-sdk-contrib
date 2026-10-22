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
// @SelectFile("test-harness/gherkin/connection.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "dev.openfeature.contrib.providers.flagd.e2e.steps")
@ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME, value = "io.cucumber.picocontainer.PicoFactory")
@IncludeTags("file")
@ExcludeTags({
    "unixsocket",
    "targetURI",
    "reconnect",
    "customCert",
    "events",
    "contextEnrichment",
    "fractional-v1",
    "fractional-v2",
    "deprecated"
})
// Parallel scenario execution, declared here rather than in a module-wide
// junit-platform.properties: every one of these settings is about *this* suite -- its ContainerPool,
// its @env-var and @grace tags -- and a file in test resources applies to every suite in the module,
// including the Provider TCK ones, which then had to pin parallelism off to defend themselves.
@ConfigurationParameter(key = PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME, value = "true")
@ConfigurationParameter(key = PARALLEL_CONFIG_STRATEGY_PROPERTY_NAME, value = "dynamic")
@ConfigurationParameter(key = PARALLEL_CONFIG_DYNAMIC_FACTOR_PROPERTY_NAME, value = "1")
// @grace scenarios restart a container and wait for reconnection; two concurrent restarts can push
// that past the event timeout.
@ConfigurationParameter(key = "cucumber.execution.exclusive-resources.grace.read-write", value = "CONTAINER_RESTART")
@Testcontainers
public class RunFileTest {

    @BeforeSuite
    public static void before() {
        State.resolverType = Config.Resolver.FILE;
    }
}
