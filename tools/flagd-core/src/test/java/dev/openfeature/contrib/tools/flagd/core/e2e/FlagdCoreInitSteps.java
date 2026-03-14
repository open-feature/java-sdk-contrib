package dev.openfeature.contrib.tools.flagd.core.e2e;

import dev.openfeature.contrib.tools.flagd.api.testkit.EvaluatorState;
import dev.openfeature.contrib.tools.flagd.api.testkit.TestkitFlags;
import dev.openfeature.contrib.tools.flagd.core.FlagdCore;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;

/**
 * Provides the {@code Given an evaluator} step for the flagd-api-testkit,
 * wiring up {@link FlagdCore} as the reference implementation under test.
 *
 * <p>This is the only step that consumers of the testkit need to implement.
 */
public class FlagdCoreInitSteps {

    private final EvaluatorState state;

    public FlagdCoreInitSteps(EvaluatorState state) {
        this.state = state;
    }

    @Before
    public void resetContext() {
        // Reset context before each scenario so state does not bleed between scenarios.
        state.context = new dev.openfeature.sdk.MutableContext();
    }

    @Given("an evaluator")
    public void aStableEvaluator() throws Exception {
        FlagdCore core = new FlagdCore();
        core.setFlags(TestkitFlags.loadFlags());
        state.setEvaluator(core);
    }
}
