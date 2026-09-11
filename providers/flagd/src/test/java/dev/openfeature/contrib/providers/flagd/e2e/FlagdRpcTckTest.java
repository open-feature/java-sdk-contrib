package dev.openfeature.contrib.providers.flagd.e2e;

import dev.openfeature.contrib.providers.flagd.Config;

/** Runs the OpenFeature Provider TCK against the flagd provider in RPC mode. */
public class FlagdRpcTckTest extends AbstractFlagdTckTest {

    @Override
    protected Config.Resolver resolver() {
        return Config.Resolver.RPC;
    }

    @Override
    protected int backendPort() {
        return 8013;
    }
}
