package dev.openfeature.contrib.tools.flagd.api.testkit;

/**
 * SPI interface for creating an {@link dev.openfeature.contrib.tools.flagd.api.Evaluator}
 * loaded with the testkit flag configuration.
 *
 * <p>Consumers extend {@link AbstractEvaluatorTest} (which implements this interface) and
 * register their concrete class in:
 * {@code META-INF/services/dev.openfeature.contrib.tools.flagd.api.testkit.EvaluatorFactory}
 *
 * <p>The testkit discovers the factory via {@link java.util.ServiceLoader} — no Cucumber
 * annotations or glue package configuration required on the consumer side.
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
