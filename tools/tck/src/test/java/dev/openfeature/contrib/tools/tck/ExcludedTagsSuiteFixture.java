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
 * A real TCK suite over {@code skip-selftest/}, run by {@link ExcludedTagsTest}. Not named
 * {@code *Test} so Surefire skips it; does not extend {@link ProviderTckTest} to avoid inheriting its selectors.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource(ExcludedTagsSuiteFixture.FEATURES)
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME, value = ProviderTck.PLUGINS)
@ConfigurationParameter(key = Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME, value = "false")
@ConfigurationParameter(
        key = Constants.EXECUTION_MODE_FEATURE_PROPERTY_NAME,
        value = ProviderTck.FEATURE_EXECUTION_MODE)
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = ProviderTck.ALL_GLUE)
@ConfigurationParameter(key = Constants.OBJECT_FACTORY_PROPERTY_NAME, value = ProviderTck.OBJECT_FACTORY)
@TckExcludeTags(ExcludedTagsSuiteFixture.EXCLUDED)
@TckIncludeTags(ExcludedTagsSuiteFixture.INCLUDED)
public class ExcludedTagsSuiteFixture implements ProviderTckHarness {

    /** The classpath directory holding this fixture's feature files. */
    public static final String FEATURES = "skip-selftest";

    /** The tag this fixture excludes. */
    public static final String EXCLUDED = "@old-version";

    /** The tag an extension scenario needs to run here. */
    public static final String INCLUDED = "@mode-a";

    private final InProcessBackendControl control = new InProcessBackendControl();

    @Override
    public BackendControl backendControl() {
        return control;
    }

    @Override
    public FeatureProvider createProvider() {
        return control.createProvider();
    }

    /** Declares {@code @events}, so only the exclusion can skip the scenario carrying it. */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(Capability.EVENTS);
    }
}
