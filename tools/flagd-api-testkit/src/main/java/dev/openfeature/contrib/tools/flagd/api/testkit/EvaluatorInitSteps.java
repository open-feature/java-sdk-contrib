package dev.openfeature.contrib.tools.flagd.api.testkit;

import dev.openfeature.sdk.MutableContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import java.util.ServiceLoader;

/**
 * Cucumber step class that owns {@code @Given("an evaluator")} and the per-scenario context reset.
 *
 * <p>Consumers do not extend or modify this class. The evaluator factory is discovered
 * automatically via Java SPI — see {@link EvaluatorFactory}.
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
     * Loads the {@link EvaluatorFactory} via Java SPI ({@link ServiceLoader}), invokes it with
     * the bundled flag configuration, and stores the resulting evaluator in {@link EvaluatorState}.
     */
    @Given("an evaluator")
    public void anEvaluator() throws Exception {
        EvaluatorFactory factory = ServiceLoader.load(EvaluatorFactory.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No EvaluatorFactory found via SPI. "
                        + "Register your implementation in "
                        + "META-INF/services/dev.openfeature.contrib.tools.flagd.api.testkit.EvaluatorFactory"));
        state.setEvaluator(factory.create(TestkitFlags.loadFlags()));
    }
}
