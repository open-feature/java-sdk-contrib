package dev.openfeature.contrib.tools.flagd.api.testkit;

import dev.openfeature.contrib.tools.flagd.api.Evaluator;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Shared Cucumber scenario state for the flagd-api testkit.
 * Injected via PicoContainer into every step definition class.
 *
 * <p>Consumers register an {@link EvaluatorFactory} lambda in their glue class constructor.
 * The testkit's {@code @Given("an evaluator")} step invokes the factory with the bundled
 * flag configuration and stores the resulting {@link Evaluator} here.
 */
public class EvaluatorState {

    private EvaluatorFactory factory;
    private Evaluator evaluator;

    /** The flag key and type under test. */
    public String flagKey;

    public String flagType;
    public Object defaultValue;

    /** Result populated by evaluation steps. */
    public ProviderEvaluation<?> evaluation;

    /** Evaluation context accumulated by context steps. */
    public MutableContext context = new MutableContext();

    /** Returns the evaluator factory registered by the consumer. */
    public EvaluatorFactory getFactory() {
        return factory;
    }

    /** Registers the evaluator factory. Call this from your glue class constructor. */
    public void setFactory(EvaluatorFactory factory) {
        this.factory = factory;
    }

    /** Returns the evaluator under test. */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Intentional mutable state sharing required by Cucumber PicoContainer DI")
    public Evaluator getEvaluator() {
        return evaluator;
    }

    /** Sets the evaluator under test. */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Intentional mutable state sharing required by Cucumber PicoContainer DI")
    public void setEvaluator(Evaluator evaluator) {
        this.evaluator = evaluator;
    }
}
