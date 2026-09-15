package dev.openfeature.contrib.providers.gofeatureflag.service;

import static dev.openfeature.sdk.Value.objectToValue;

import dev.openfeature.contrib.providers.gofeatureflag.evaluator.IEvaluator;
import dev.openfeature.contrib.providers.gofeatureflag.util.MetadataUtil;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import lombok.AllArgsConstructor;
import lombok.val;

/**
 * EvaluationService is responsible for evaluating feature flags using the provided evaluator.
 * It can use different evaluators based on the configuration and context.
 */
@AllArgsConstructor
public class EvaluationService {
    /**
     * The evaluator used to evaluate the flags.
     */
    private IEvaluator evaluator;

    /**
     * Return true if we should track the usage of the flag.
     *
     * @param flagKey - name of the flag
     * @return true if the flag is trackable, false otherwise
     */
    public boolean isFlagTrackable(final String flagKey) {
        return this.evaluator.isFlagTrackable(flagKey);
    }

    /**
     * Init the evaluator.
     */
    public void init() {
        this.evaluator.init();
    }

    /**
     * Destroy the evaluator.
     */
    public void destroy() {
        this.evaluator.destroy();
    }

    /**
     * Get the evaluation response from the evaluator.
     *
     * @param flagKey           - name of the flag
     * @param defaultValue      - default value
     * @param evaluationContext - evaluation context
     * @param expectedType      - expected type of the value
     * @param <T>               - type of the value
     * @return the evaluation response
     */
    public <T> ProviderEvaluation<T> getEvaluation(
            String flagKey, T defaultValue, EvaluationContext evaluationContext, Class<?> expectedType) {

        val goffResp = evaluator.evaluate(flagKey, defaultValue, evaluationContext);

        // Check for FLAG_NOT_FOUND error first, before general error handling
        if (goffResp.getErrorCode() != null
                && ErrorCode.FLAG_NOT_FOUND.name().equalsIgnoreCase(goffResp.getErrorCode())) {
            throw new FlagNotFoundError("Flag " + flagKey + " was not found in your configuration");
        }

        // If we have an error code, we return the error directly.
        if (goffResp.getErrorCode() != null && !goffResp.getErrorCode().isEmpty()) {
            return ProviderEvaluation.<T>builder()
                    .errorCode(mapErrorCode(goffResp.getErrorCode()))
                    .errorMessage(goffResp.getErrorDetails())
                    .reason(Reason.ERROR.name())
                    .value(defaultValue)
                    .build();
        }

        if (Reason.DISABLED.name().equalsIgnoreCase(goffResp.getReason())) {
            // we don't set a variant since we are using the default value,
            // and we are not able to know which variant it is.
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .variant(goffResp.getVariationType())
                    .reason(Reason.DISABLED.name())
                    .build();
        }

        // Convert the value received from the API.
        T flagValue = convertValue(goffResp.getValue(), expectedType);

        if (flagValue.getClass() != expectedType) {
            throw new TypeMismatchError(String.format(
                    "Flag value %s had unexpected type %s, expected %s.", flagKey, flagValue.getClass(), expectedType));
        }

        return ProviderEvaluation.<T>builder()
                .errorCode(mapErrorCode(goffResp.getErrorCode()))
                .reason(goffResp.getReason())
                .value(flagValue)
                .variant(goffResp.getVariationType())
                .flagMetadata(MetadataUtil.convertFlagMetadata(goffResp.getMetadata()))
                .build();
    }

    /**
     * convertValue is converting the object return by the proxy response in the right type.
     *
     * @param value        - The value we have received
     * @param expectedType - the type we expect for this value
     * @param <T>          the type we want to convert to.
     * @return A converted object
     */
    // The cast to T is unchecked on purpose: getEvaluation compares the runtime class with
    // expectedType right after and raises a TypeMismatchError if the API returned another type.
    @SuppressWarnings("unchecked")
    private <T> T convertValue(Object value, Class<?> expectedType) {
        boolean isPrimitive = expectedType == Boolean.class
                || expectedType == String.class
                || expectedType == Integer.class
                || expectedType == Double.class;

        if (isPrimitive) {
            // JSON does not distinguish 100 from 100.0, and a number too large for an int decodes to
            // Long, so the float resolver accepts any number rather than only Integer.
            if (expectedType == Double.class && value instanceof Number) {
                return (T) Double.valueOf(((Number) value).doubleValue());
            }
            return (T) value;
        }
        return (T) objectToValue(value);
    }

    /**
     * mapErrorCode is mapping the errorCode in string received by the API to our internal SDK
     * ErrorCode enum.
     *
     * @param errorCode - string of the errorCode received from the API
     * @return an item from the enum
     */
    private ErrorCode mapErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isEmpty()) {
            return null;
        }

        try {
            return ErrorCode.valueOf(errorCode);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
