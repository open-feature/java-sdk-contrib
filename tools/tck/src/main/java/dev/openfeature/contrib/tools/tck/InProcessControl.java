package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.providers.memory.InMemoryProvider;

/**
 * A {@link BackendControl} that also supplies the provider, for a provider with no backend.
 *
 * <p>In-process the flag store and the provider are one object, so {@code changeFlag()} has to
 * reach the live instance. Obtain one from {@link BackendControl#inProcess()}.
 */
public interface InProcessControl extends BackendControl {

    /**
     * Returns the provider this control is the flag store for.
     *
     * @return a provider over an untouched copy of the canonical flag set
     */
    InMemoryProvider createProvider();
}
