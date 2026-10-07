package dev.openfeature.contrib.providers.flagd.tck;

import dev.openfeature.contrib.providers.flagd.Config;

/** Runs the OpenFeature Provider TCK against the flagd provider in RPC mode. */
public class RpcTest extends AbstractResolverTest {

    /**
     * {@inheritDoc}
     *
     * <p>Stated rather than derived from the class name, which says only which resolver this is: a
     * report is read away from this repository, where {@code rpc} on its own would not say whose RPC
     * resolver it was.
     */
    @Override
    public String configuration() {
        return "flagd-rpc";
    }

    @Override
    protected Config.Resolver resolver() {
        return Config.Resolver.RPC;
    }

    @Override
    protected int backendPort() {
        return 8013;
    }
}
