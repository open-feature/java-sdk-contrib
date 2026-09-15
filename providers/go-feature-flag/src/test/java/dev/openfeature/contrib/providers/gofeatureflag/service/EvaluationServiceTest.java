package dev.openfeature.contrib.providers.gofeatureflag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.evaluator.IEvaluator;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EvaluationService tests")
class EvaluationServiceTest {
    private IEvaluator mockEvaluator;
    private EvaluationService evaluationService;
    private EvaluationContext evaluationContext;

    @BeforeEach
    void setUp() {
        mockEvaluator = mock(IEvaluator.class);
        evaluationService = new EvaluationService(mockEvaluator);
        evaluationContext = new ImmutableContext("test-targeting-key");
    }

    @DisplayName("Should throw FlagNotFoundError when flag is not found")
    @Test
    void shouldThrowFlagNotFoundErrorWhenFlagIsNotFound() {
        // Given: evaluator returns a response with FLAG_NOT_FOUND error code
        GoFeatureFlagResponse response = new GoFeatureFlagResponse();
        response.setErrorCode(ErrorCode.FLAG_NOT_FOUND.name());
        response.setErrorDetails("Flag test-flag was not found in your configuration");
        response.setValue(false);

        when(mockEvaluator.evaluate(anyString(), any(), any(EvaluationContext.class)))
                .thenReturn(response);

        // When/Then: getEvaluation should throw FlagNotFoundError
        FlagNotFoundError exception = assertThrows(
                FlagNotFoundError.class,
                () -> evaluationService.getEvaluation("test-flag", false, evaluationContext, Boolean.class));

        assertEquals("Flag test-flag was not found in your configuration", exception.getMessage());
    }

    @DisplayName("Should return error response for other error codes")
    @Test
    void shouldReturnErrorResponseForOtherErrorCodes() {
        // Given: evaluator returns a response with a different error code
        GoFeatureFlagResponse response = new GoFeatureFlagResponse();
        response.setErrorCode(ErrorCode.GENERAL.name());
        response.setErrorDetails("Some other error occurred");
        response.setValue(false);

        when(mockEvaluator.evaluate(anyString(), any(), any(EvaluationContext.class)))
                .thenReturn(response);

        // When: getEvaluation is called
        ProviderEvaluation<Boolean> result =
                evaluationService.getEvaluation("test-flag", false, evaluationContext, Boolean.class);

        // Then: should return error response, not throw exception
        assertEquals(ErrorCode.GENERAL, result.getErrorCode());
        assertEquals("Some other error occurred", result.getErrorMessage());
        assertEquals(Reason.ERROR.name(), result.getReason());
        assertEquals(false, result.getValue());
    }

    @DisplayName("Should handle successful evaluation")
    @Test
    void shouldHandleSuccessfulEvaluation() {
        // Given: evaluator returns a successful response
        GoFeatureFlagResponse response = new GoFeatureFlagResponse();
        response.setValue(true);
        response.setReason(Reason.TARGETING_MATCH.name());
        response.setVariationType("enabled");
        response.setErrorCode(null);

        when(mockEvaluator.evaluate(anyString(), any(), any(EvaluationContext.class)))
                .thenReturn(response);

        // When: getEvaluation is called
        ProviderEvaluation<Boolean> result =
                evaluationService.getEvaluation("test-flag", false, evaluationContext, Boolean.class);

        // Then: should return successful evaluation
        assertEquals(true, result.getValue());
        assertEquals(Reason.TARGETING_MATCH.name(), result.getReason());
        assertEquals("enabled", result.getVariant());
        assertEquals(null, result.getErrorCode());
    }

    @DisplayName("Should accept an int number for the double resolver")
    @Test
    void shouldAcceptAnIntegralNumberForTheDoubleResolver() {
        assertEquals(100.0, doubleEvaluationOf(100));
    }

    @DisplayName("Should accept an int number larger than an int for the double resolver")
    @Test
    void shouldAcceptAnIntegralNumberLargerThanAnIntForTheDoubleResolver() {
        // above Integer.MAX_VALUE Jackson decodes a JSON integer to Long
        assertEquals(3000000000.0, doubleEvaluationOf(3000000000L));
    }

    @DisplayName("Should accept a decimal number for the double resolver")
    @Test
    void shouldAcceptADecimalNumberForTheDoubleResolver() {
        assertEquals(101.25, doubleEvaluationOf(101.25));
    }

    @DisplayName("Should not let a boolean satisfy the double resolver")
    @Test
    void shouldNotLetABooleanSatisfyTheDoubleResolver() {
        when(mockEvaluator.evaluate(anyString(), any(), any(EvaluationContext.class)))
                .thenReturn(responseWithValue(true));

        assertThrows(
                TypeMismatchError.class,
                () -> evaluationService.getEvaluation("test-flag", 0.0, evaluationContext, Double.class));
    }

    @DisplayName("Should not let a decimal number satisfy the integer resolver")
    @Test
    void shouldNotLetADecimalNumberSatisfyTheIntegerResolver() {
        when(mockEvaluator.evaluate(anyString(), any(), any(EvaluationContext.class)))
                .thenReturn(responseWithValue(101.25));

        assertThrows(
                TypeMismatchError.class,
                () -> evaluationService.getEvaluation("test-flag", 0, evaluationContext, Integer.class));
    }

    private Double doubleEvaluationOf(Object engineValue) {
        when(mockEvaluator.evaluate(anyString(), any(), any(EvaluationContext.class)))
                .thenReturn(responseWithValue(engineValue));
        return evaluationService
                .getEvaluation("test-flag", 0.0, evaluationContext, Double.class)
                .getValue();
    }

    private GoFeatureFlagResponse responseWithValue(Object value) {
        GoFeatureFlagResponse response = new GoFeatureFlagResponse();
        response.setValue(value);
        response.setReason(Reason.TARGETING_MATCH.name());
        response.setVariationType("enabled");
        return response;
    }

    @DisplayName("Should return the caller default and keep the engine details when the value is null")
    @Test
    void shouldReturnTheCallerDefaultAndKeepTheEngineDetailsWhenTheValueIsNull() {
        GoFeatureFlagResponse response = responseWithValue(null);
        response.setMetadata(Map.of("description", "a flag with no value"));

        when(mockEvaluator.evaluate(anyString(), any(), any(EvaluationContext.class)))
                .thenReturn(response);

        ProviderEvaluation<Boolean> result =
                evaluationService.getEvaluation("test-flag", true, evaluationContext, Boolean.class);

        assertEquals(true, result.getValue());
        assertEquals(Reason.DEFAULT.name(), result.getReason());
        assertNull(result.getVariant());
        assertEquals("a flag with no value", result.getFlagMetadata().getString("description"));
        assertNull(result.getErrorCode());
    }

    @DisplayName("Should not return a zero value when the value is null")
    @Test
    void shouldNotReturnAZeroValueWhenTheValueIsNull() {
        when(mockEvaluator.evaluate(anyString(), any(), any(EvaluationContext.class)))
                .thenReturn(responseWithValue(null));

        assertEquals(
                42,
                evaluationService
                        .getEvaluation("test-flag", 42, evaluationContext, Integer.class)
                        .getValue());
        assertEquals(
                "caller-default",
                evaluationService
                        .getEvaluation("test-flag", "caller-default", evaluationContext, String.class)
                        .getValue());
        assertEquals(
                new Value("caller-default"),
                evaluationService
                        .getEvaluation("test-flag", new Value("caller-default"), evaluationContext, Value.class)
                        .getValue());
    }
}
