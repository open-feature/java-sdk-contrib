package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.FeatureProvider;
import java.util.EnumSet;
import java.util.Set;

/**
 * Runs the suite against a provider with a real, failable initialisation, covering the lifecycle
 * scenarios without Docker.
 */
public class ControllableProviderTckTest extends ProviderTckTest {

    private final ControllableBackendControl control = new ControllableBackendControl();

    @Override
    public BackendControl backendControl() {
        return control;
    }

    @Override
    public FeatureProvider createProvider() {
        return control.createProvider();
    }

    /** A provider over an in-JVM store that refuses to answer. */
    @Override
    public FeatureProvider createUnavailableProvider() {
        return control.createUnavailableProvider();
    }

    /** {@link InMemoryProviderTckTest}'s set plus LIFECYCLE, REINITIALIZATION and UNAVAILABLE_INIT. */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(
                Capability.EVENTS,
                Capability.LIFECYCLE,
                Capability.REINITIALIZATION,
                Capability.UNAVAILABLE_INIT,
                Capability.CONFIGURATION_CHANGE,
                Capability.OBJECT,
                Capability.VARIANTS,
                Capability.DISABLED_FLAGS,
                Capability.STRING_TYPING,
                Capability.FULLY_TYPED_VALUES,
                Capability.STANDARD_REASONS);
    }
}
