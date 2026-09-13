package dev.openfeature.contrib.providers.flagd.tck;

import dev.openfeature.contrib.providers.flagd.Config;

/** Runs the OpenFeature Provider TCK against the flagd provider in RPC mode. */
public class RpcTest extends AbstractResolverTest {

    /**
     * {@inheritDoc}
     *
     * <p>Stated rather than derived. The default derivation reads the suite's class name, which
     * used to be {@code FlagdRpcTckTest} and now says only which resolver it is, because the
     * package says the rest. A report is read away from this repository, where {@code rpc} on its
     * own would not say whose RPC resolver it was, so the name a run is filed under is written here
     * instead of moving with the class name.
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
