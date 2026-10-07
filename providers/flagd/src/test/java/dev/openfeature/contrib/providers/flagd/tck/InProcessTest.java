package dev.openfeature.contrib.providers.flagd.tck;

import dev.openfeature.contrib.providers.flagd.Config;

/** Runs the OpenFeature Provider TCK against the flagd provider in in-process mode. */
public class InProcessTest extends AbstractResolverTest {

    /**
     * {@inheritDoc}
     *
     * <p>Stated rather than derived, for the reason {@link RpcTest#configuration()} gives.
     */
    @Override
    public String configuration() {
        return "flagd-in-process";
    }

    @Override
    protected Config.Resolver resolver() {
        return Config.Resolver.IN_PROCESS;
    }

    @Override
    protected int backendPort() {
        return 8015;
    }
}
