package dev.openfeature.contrib.tools.flagd.api.testkit;

import dev.openfeature.sdk.MutableContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;

/**
 * Cucumber step class that owns {@code @Given("an evaluator")} and the per-scenario context reset.
 *
 * <p>Consumers do not extend or modify this class. Instead, register an {@link EvaluatorFactory}
 * lambda in your own glue class constructor:
 *
 * <pre>{@code
 * public class MyEvaluatorSetup {
 *     public MyEvaluatorSetup(EvaluatorState state) {
 *         state.setFactory(flagsJson -> {
 *             MyEvaluator evaluator = new MyEvaluator();
 *             evaluator.setFlags(flagsJson);
 *             return evaluator;
 *         });
 *     }
 * }
 * }</pre>
 */
public class EvaluatorInitSteps {

    private final EvaluatorState state;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Intentional mutable state sharing required by Cucumber PicoContainer DI")
    public EvaluatorInitSteps(EvaluatorState state) {
        this.state = state;
    }

    /** Resets the evaluation context before each scenario to prevent state bleed. */
    @Before
    public void resetContext() {
        state.context = new MutableContext();
    }

    /**
     * Invokes the registered {@link EvaluatorFactory} with the bundled flag configuration
     * and stores the resulting evaluator in {@link EvaluatorState}.
     */
    @Given("an evaluator")
    public void anEvaluator() throws Exception {
        EvaluatorFactory factory = state.getFactory();
        if (factory == null) {
            throw new IllegalStateException(
                    "No EvaluatorFactory registered. " + "Call state.setFactory(...) in your glue class constructor.");
        }
        state.setEvaluator(factory.create(TestkitFlags.loadFlags()));
    }
}
