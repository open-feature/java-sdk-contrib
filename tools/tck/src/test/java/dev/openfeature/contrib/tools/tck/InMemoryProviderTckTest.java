package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import java.util.EnumSet;
import java.util.Set;

/**
 * Runs the TCK against the SDK's {@link InMemoryProvider}; the reference adoption for a provider with
 * no backend, and a fast Docker-free canary.
 */
public class InMemoryProviderTckTest extends ProviderTckTest {

    /** Both the flag store and the provider factory, since changeFlag() must reach the live provider. */
    private final InProcessBackendControl control = new InProcessBackendControl();

    @Override
    public BackendControl backendControl() {
        return control;
    }

    @Override
    public FeatureProvider createProvider() {
        return control.createProvider();
    }

    /** Omissions reflect InMemoryProvider properties (strict typing, no rules, no connection), not defects. */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(
                Capability.EVENTS,
                Capability.CONFIGURATION_CHANGE,
                Capability.OBJECT,
                Capability.VARIANTS,
                Capability.DISABLED_FLAGS,
                Capability.STRING_TYPING,
                Capability.FULLY_TYPED_VALUES,
                Capability.STANDARD_REASONS);
    }
}
