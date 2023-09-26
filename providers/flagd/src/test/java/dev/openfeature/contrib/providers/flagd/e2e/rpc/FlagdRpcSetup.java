package dev.openfeature.contrib.providers.flagd.e2e.rpc;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Isolated;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.steps.StepDefinitions;
import dev.openfeature.sdk.FeatureProvider;
import io.cucumber.java.BeforeAll;

@Isolated()
@Order(value = Integer.MAX_VALUE)
public class FlagdRpcSetup {

    private static FeatureProvider provider;

    @BeforeAll()
    public static void setup() {
        FlagdRpcSetup.provider = new FlagdProvider(FlagdOptions.builder()
        .resolverType(Config.Evaluator.RPC)
        // set a generous deadline, to prevent timeouts in actions
        .deadline(3000)
        .build());
        StepDefinitions.setProvider(provider);
    }
}
