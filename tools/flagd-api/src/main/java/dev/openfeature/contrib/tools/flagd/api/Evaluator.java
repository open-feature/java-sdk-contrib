package dev.openfeature.contrib.tools.flagd.api;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import java.util.List;
import java.util.Map;

/**
 * Interface for in-process flag evaluation in flagd.
 * Combines flag storage and evaluation into a single abstraction.
 */
public interface Evaluator {

    /**
     * Set flag configurations from a JSON string.
     *
     * @param flagConfigurationJson the flag configuration JSON string
     * @throws FlagStoreException if parsing or setting fails
     */
    void setFlags(String flagConfigurationJson) throws FlagStoreException;

    /**
     * Set flag configurations and return the list of changed flag keys.
     * This is useful for emitting configuration change events.
     *
     * @param flagConfigurationJson the flag configuration JSON string
     * @return the list of flag keys that were changed (added, modified, or removed)
     * @throws FlagStoreException if parsing or setting fails
     */
    List<String> setFlagsAndGetChangedKeys(String flagConfigurationJson) throws FlagStoreException;

    /**
     * Get the current flag set metadata.
     * Flag set metadata is defined at the top level of the flag configuration.
     *
     * @return the flag set metadata (unmodifiable view)
     */
    Map<String, Object> getFlagSetMetadata();

    /**
     * Resolve a boolean flag value.
     *
     * @param flagKey the flag key
     * @param ctx      the evaluation context
     * @return the resolution result
     */
    ProviderEvaluation<Boolean> resolveBooleanValue(String flagKey, EvaluationContext ctx);

    /**
     * Resolve a string flag value.
     *
     * @param flagKey the flag key
     * @param ctx      the evaluation context
     * @return the resolution result
     */
    ProviderEvaluation<String> resolveStringValue(String flagKey, EvaluationContext ctx);

    /**
     * Resolve an integer flag value.
     *
     * @param flagKey the flag key
     * @param ctx      the evaluation context
     * @return the resolution result
     */
    ProviderEvaluation<Integer> resolveIntegerValue(String flagKey, EvaluationContext ctx);

    /**
     * Resolve a double/float flag value.
     *
     * @param flagKey the flag key
     * @param ctx      the evaluation context
     * @return the resolution result
     */
    ProviderEvaluation<Double> resolveDoubleValue(String flagKey, EvaluationContext ctx);

    /**
     * Resolve an object flag value.
     *
     * @param flagKey the flag key
     * @param ctx      the evaluation context
     * @return the resolution result
     */
    ProviderEvaluation<Value> resolveObjectValue(String flagKey, EvaluationContext ctx);
}
