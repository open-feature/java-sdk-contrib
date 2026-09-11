package dev.openfeature.contrib.providers.flagd.e2e;

import dev.openfeature.contrib.providers.flagd.Config;

/** Runs the OpenFeature Provider TCK against the flagd provider in in-process mode. */
public class FlagdInProcessTckTest extends AbstractFlagdTckTest {

    @Override
    protected Config.Resolver resolver() {
        return Config.Resolver.IN_PROCESS;
    }

    @Override
    protected int backendPort() {
        return 8015;
    }
}
