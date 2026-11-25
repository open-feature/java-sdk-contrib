package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.multiprovider.MultiProvider;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Runs the TCK against {@link MultiProvider} wrapping a single {@link InMemoryProvider}, to verify
 * delegation is transparent.
 */
public class MultiProviderTckTest extends ProviderTckTest {

    private final InProcessBackendControl control = new InProcessBackendControl();

    @Override
    public BackendControl backendControl() {
        return control;
    }

    @Override
    public FeatureProvider createProvider() {
        return new MultiProvider(Collections.singletonList(control.createProvider()));
    }

    /**
     * Omits CONFIGURATION_CHANGE since MultiProvider swallows child events; remove once
     * <a href="https://github.com/open-feature/java-sdk/issues/1882">java-sdk#1882</a> is fixed.
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(
                Capability.EVENTS,
                Capability.OBJECT,
                Capability.VARIANTS,
                Capability.DISABLED_FLAGS,
                Capability.STRING_TYPING,
                Capability.FULLY_TYPED_VALUES,
                Capability.STANDARD_REASONS);
    }
}
