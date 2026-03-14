package dev.openfeature.contrib.tools.flagd.core.e2e;

import dev.openfeature.contrib.tools.flagd.api.testkit.EvaluatorState;
import dev.openfeature.contrib.tools.flagd.core.FlagdCore;
import io.cucumber.java.Before;

/**
 * Registers {@link FlagdCore} as the {@link dev.openfeature.contrib.tools.flagd.api.Evaluator}
 * under test. Serves as the reference consumer of the flagd-api-testkit.
 *
 * <p>The {@code @Before} hook registers the factory lambda on {@link EvaluatorState} before
 * each scenario. The testkit's {@code @Given("an evaluator")} step then invokes it with the
 * bundled flag JSON.
 */
public class FlagdCoreInitSteps {

    private final EvaluatorState state;

    public FlagdCoreInitSteps(EvaluatorState state) {
        this.state = state;
    }

    @Before
    public void registerFactory() {
        state.setFactory(flagsJson -> {
            FlagdCore core = new FlagdCore();
            core.setFlags(flagsJson);
            return core;
        });
    }
}
