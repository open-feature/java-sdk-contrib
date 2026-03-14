package dev.openfeature.contrib.tools.flagd.api.testkit;

import dev.openfeature.contrib.tools.flagd.api.Evaluator;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Shared Cucumber scenario state for the flagd-api testkit.
 * Injected via PicoContainer into every step definition class.
 *
 * <p>Consumers must provide one step that creates an {@link Evaluator} and
 * assigns it here via {@link #setEvaluator(Evaluator)}.
 */
public class EvaluatorState {

    private Evaluator evaluator;

    /** The flag key and type under test. */
    public String flagKey;

    public String flagType;
    public Object defaultValue;

    /** Result populated by evaluation steps. */
    public ProviderEvaluation<?> evaluation;

    /** Evaluation context accumulated by context steps. */
    public MutableContext context = new MutableContext();

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
