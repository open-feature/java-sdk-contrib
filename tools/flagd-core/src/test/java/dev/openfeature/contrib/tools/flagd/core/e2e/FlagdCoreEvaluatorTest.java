package dev.openfeature.contrib.tools.flagd.core.e2e;

import dev.openfeature.contrib.tools.flagd.api.Evaluator;
import dev.openfeature.contrib.tools.flagd.api.testkit.AbstractEvaluatorTest;
import dev.openfeature.contrib.tools.flagd.core.FlagdCore;
import org.junit.platform.suite.api.ExcludeTags;

/**
 * Compliance test suite for {@link FlagdCore} — the reference implementation of the
 * flagd-api-testkit. Extends {@link AbstractEvaluatorTest} which provides all runner
 * configuration. Registered as an {@link dev.openfeature.contrib.tools.flagd.api.testkit.EvaluatorFactory}
 * via {@code META-INF/services}.
 */
// large-integers asks for 2^53 - 1, which Evaluator's ProviderEvaluation<Integer> cannot carry
@ExcludeTags({
    "fractional-v1",
    "fractional-v2",
    "evaluator-refs-whitespace",
    "non-existent-evaluator-ref",
    "large-integers"
})
public class FlagdCoreEvaluatorTest extends AbstractEvaluatorTest {

    @Override
    public Evaluator create(String flagsJson) throws Exception {
        FlagdCore core = new FlagdCore();
        core.setFlags(flagsJson);
        return core;
    }
}
