package dev.openfeature.contrib.providers.flagd.e2e.reconnect.rpc;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Isolated;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.reconnect.steps.StepDefinitions;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.CacheType;
import dev.openfeature.sdk.FeatureProvider;
import io.cucumber.java.BeforeAll;

@Isolated()
@Order(value = Integer.MAX_VALUE)
public class FlagdRpcSetup {

    @BeforeAll()
    public static void setup() throws InterruptedException {
        FeatureProvider workingProvider = new FlagdProvider(FlagdOptions.builder()
                .resolverType(Config.Evaluator.RPC)
                .port(8014)
                // set a generous deadline, to prevent timeouts in actions
                .deadline(3000)
                .cacheType(CacheType.DISABLED.getValue())
                .build());
        StepDefinitions.setUnstableProvider(workingProvider);

        FeatureProvider unavailableProvider = new FlagdProvider(FlagdOptions.builder()
                .resolverType(Config.Evaluator.RPC)
                .port(8015) // this port isn't serving anything, error expected
                // set a generous deadline, to prevent timeouts in actions
                .deadline(100)
                .cacheType(CacheType.DISABLED.getValue())
                .build());
        StepDefinitions.setUnavailableProvider(unavailableProvider);
    }
}
