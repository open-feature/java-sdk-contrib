package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;

/**
 * IEvaluator is an interface that represents the evaluation of a feature flag.
 * It can have multiple implementations: REMOTE or IN-PROCESS.
 */
public interface IEvaluator {
    /**
     * initialize is called when the provider is initialized for a specific domain.
     *
     * @param ctx    - evaluation context
     * @param domain - domain the provider is bound to
     * @throws Exception - if the evaluator cannot be initialized
     */
    void initialize(final EvaluationContext ctx, final String domain) throws Exception;

    /**
     * initialize is called when the provider is initialized.
     *
     * @param ctx - evaluation context
     * @throws Exception - if the evaluator cannot be initialized
     */
    void initialize(final EvaluationContext ctx) throws Exception;

    /**
     * shutdown releases everything the evaluator holds, so that it stops doing background work.
     */
    void shutdown();

    /**
     * getBooleanEvaluation resolves the value of a boolean flag.
     *
     * @param key          - name of the flag
     * @param defaultValue - default value provided by the caller
     * @param ctx          - evaluation context
     * @return the evaluation result for this flag
     */
    ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx);

    /**
     * getStringEvaluation resolves the value of a string flag.
     *
     * @param key          - name of the flag
     * @param defaultValue - default value provided by the caller
     * @param ctx          - evaluation context
     * @return the evaluation result for this flag
     */
    ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx);

    /**
     * getIntegerEvaluation resolves the value of an integer flag.
     *
     * @param key          - name of the flag
     * @param defaultValue - default value provided by the caller
     * @param ctx          - evaluation context
     * @return the evaluation result for this flag
     */
    ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx);

    /**
     * getDoubleEvaluation resolves the value of a float flag.
     *
     * @param key          - name of the flag
     * @param defaultValue - default value provided by the caller
     * @param ctx          - evaluation context
     * @return the evaluation result for this flag
     */
    ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx);

    /**
     * getObjectEvaluation resolves the value of a flag holding a structure.
     *
     * @param key          - name of the flag
     * @param defaultValue - default value provided by the caller
     * @param ctx          - evaluation context
     * @return the evaluation result for this flag
     */
    ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx);

    /**
     * isFlagTrackable returns true if we should collect the usage of this flag.
     *
     * @param flagKey - name of the flag
     * @return true if the flag is trackable, false otherwise
     */
    boolean isFlagTrackable(String flagKey);
}
