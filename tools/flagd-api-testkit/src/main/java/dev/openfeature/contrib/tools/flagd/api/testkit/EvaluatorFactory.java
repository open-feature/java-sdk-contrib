package dev.openfeature.contrib.tools.flagd.api.testkit;

/**
 * Factory for creating an {@link dev.openfeature.contrib.tools.flagd.api.Evaluator} instance
 * loaded with the testkit flag configuration.
 *
 * <p>Register a lambda implementation via {@link EvaluatorState#setFactory(EvaluatorFactory)}
 * inside a {@code @Before} hook — this ensures Cucumber discovers your class and the factory
 * is set before the {@code @Given("an evaluator")} step fires:
 *
 * <pre>{@code
 * public class MyEvaluatorSetup {
 *
 *     private final EvaluatorState state;
 *
 *     public MyEvaluatorSetup(EvaluatorState state) {
 *         this.state = state;
 *     }
 *
 *     @Before
 *     public void registerFactory() {
 *         state.setFactory(flagsJson -> {
 *             MyEvaluator evaluator = new MyEvaluator();
 *             evaluator.setFlags(flagsJson);
 *             return evaluator;
 *         });
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface EvaluatorFactory {

    /**
     * Creates a ready-to-use evaluator loaded with the provided flag configuration JSON.
     *
     * @param flagsJson the testkit flag configuration in flagd JSON format
     * @return a configured {@link dev.openfeature.contrib.tools.flagd.api.Evaluator}
     * @throws Exception if the evaluator cannot be created or flags cannot be loaded
     */
    dev.openfeature.contrib.tools.flagd.api.Evaluator create(String flagsJson) throws Exception;
}
