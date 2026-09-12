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
 * A real TCK suite over {@code reserved-selftest/}, used by {@link ReservedTagExpiryTest}.
 *
 * <p>Executed rather than discovered: the rule under test lives in a {@code @Before} hook, so the
 * only way to prove it is to run scenarios through it. The suite is otherwise ordinary — the
 * canonical glue, the canonical object factory, an {@link InProcessBackendControl} over the SDK's
 * in-memory provider — so what runs is the path an adopter's run takes, not a hand-built
 * {@code Scenario}.
 *
 * <p>It does <strong>not</strong> extend {@link ProviderTckTest}, and that is the point of writing
 * the annotations out. The suite engine collects {@code @SelectClasspathResource} from the whole
 * class hierarchy, so a subclass of {@link ProviderTckTest} would select {@code gherkin/} and
 * {@code extensions/} as well and run the entire canonical set to observe two scenarios. Here the
 * selection is exactly one directory, which is also why that directory is not under
 * {@code extensions/}: every other suite in this module selects that one, and this fixture's second
 * scenario is meant to fail.
 *
 * <p>Deliberately not named {@code *Test}, so Surefire does not find it and run it as a suite of its
 * own — which would fail the build, correctly, and for the reason this fixture exists.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource(ReservedTagSuiteFixture.FEATURES)
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME, value = ProviderTck.PLUGINS)
@ConfigurationParameter(
        key = Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME,
        value = ProviderTck.PARALLEL_EXECUTION_ENABLED)
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

    /**
     * {@inheritDoc}
     *
     * <p>Empty, and it has to be: {@code @caching} is reserved, so there is no declaration that
     * would let the tagged scenario through. Every other scenario in the fixture is untagged and
     * therefore mandatory, so nothing here rests on the declaration at all — which is what makes the
     * failure this suite produces attributable to the reserved tag and to nothing else.
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.noneOf(Capability.class);
    }
}
