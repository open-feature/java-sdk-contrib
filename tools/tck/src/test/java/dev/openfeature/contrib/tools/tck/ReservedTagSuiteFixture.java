package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.FeatureProvider;
import io.cucumber.junit.platform.engine.Constants;
import java.util.EnumSet;
import java.util.Set;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * A real TCK suite over {@code reserved-selftest/}, run by {@link ReservedTagExpiryTest}. Not named
 * {@code *Test} so Surefire skips it; does not extend {@link ProviderTckTest} to avoid inheriting its selectors.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource(ReservedTagSuiteFixture.FEATURES)
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME, value = ProviderTck.PLUGINS)
@ConfigurationParameter(
        key = Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME,
        value = "false")
@ConfigurationParameter(
        key = Constants.EXECUTION_MODE_FEATURE_PROPERTY_NAME,
        value = ProviderTck.FEATURE_EXECUTION_MODE)
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = ProviderTck.ALL_GLUE)
@ConfigurationParameter(key = Constants.OBJECT_FACTORY_PROPERTY_NAME, value = ProviderTck.OBJECT_FACTORY)
public class ReservedTagSuiteFixture implements ProviderTckHarness {

    /** The classpath directory holding this fixture's feature file. */
    public static final String FEATURES = "reserved-selftest";

    private final InProcessBackendControl control = new InProcessBackendControl();

    @Override
    public BackendControl backendControl() {
        return control;
    }

    @Override
    public FeatureProvider createProvider() {
        return control.createProvider();
    }

    /** Empty; reserved tags cannot be declared. */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.noneOf(Capability.class);
    }
}
