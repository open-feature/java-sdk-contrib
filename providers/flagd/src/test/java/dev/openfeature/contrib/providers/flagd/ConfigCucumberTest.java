package dev.openfeature.contrib.providers.flagd;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.jupiter.api.Order;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectFile;
import org.junit.platform.suite.api.Suite;

/**
 * Runs the flagd-testbed's config.feature, which checks option defaults, environment variables
 * and builder validation. No backend is involved.
 */
@Order(value = Integer.MAX_VALUE)
@Suite
@IncludeEngines("cucumber")
@SelectFile("test-harness/gherkin/config.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "dev.openfeature.contrib.providers.flagd.config")
// The TCK ships its own Cucumber ObjectFactory, so two are on the test classpath and Cucumber
// refuses to guess.
@ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME, value = "io.cucumber.picocontainer.PicoFactory")
// Config scenarios read System env vars in FlagdOptions.build() and some scenarios also
// mutate them. Parallel execution causes env-var races (e.g. FLAGD_PORT=3456 leaking into
// a "Default Config" scenario that expects 8015). Since the entire suite runs in <0.4s,
// parallelism offers no benefit here — run sequentially for correctness.
@ConfigurationParameter(key = PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME, value = "false")
public class ConfigCucumberTest {}
