package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import static dev.openfeature.contrib.providers.gofeatureflag.evaluator.InProcessEvaluator.toProviderEvaluation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.api.GoFeatureFlagApi;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.exception.FlagConfigurationEndpointNotFound;
import dev.openfeature.contrib.providers.gofeatureflag.util.GoffApiMock;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import java.io.IOException;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.val;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InProcessEvaluatorTest {
    private static final long POLLING_INTERVAL_MS = 100L;

    private MockWebServer server;
    private GoffApiMock goffApiMock;

    @BeforeEach
    void beforeEach() throws IOException {
        this.server = new MockWebServer();
        this.goffApiMock = new GoffApiMock(GoffApiMock.MockMode.DEFAULT);
        this.server.setDispatcher(goffApiMock.dispatcher);
        this.server.start();
    }

    @AfterEach
    void afterEach() throws IOException {
        this.server.close();
        this.server = null;
    }

    @SneakyThrows
    private InProcessEvaluator evaluator(MockWebServer srv) {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(srv.url("").toString())
                .flagChangePollingIntervalMs(POLLING_INTERVAL_MS)
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        return new InProcessEvaluator(api, options, details -> {});
    }

    @SneakyThrows
    @DisplayName("a second init should not leave a second poller running")
    @Test
    void aSecondInitShouldNotLeaveASecondPollerRunning() {
        val evaluator = evaluator(this.server);
        evaluator.initialize(new ImmutableContext());
        evaluator.initialize(new ImmutableContext());

        // let both a stranded poller and the live one have several chances to fire
        Thread.sleep(POLLING_INTERVAL_MS * 5);
        evaluator.shutdown();

        // whatever was in flight when shutdown() was called may still land, so settle first
        Thread.sleep(POLLING_INTERVAL_MS * 2);
        val afterShutdown = goffApiMock.getConfigurationCallCount();
        assertTrue(afterShutdown > 0, "the evaluator should have polled at least once");

        // a poller stranded by the second init would keep calling the API forever
        Thread.sleep(POLLING_INTERVAL_MS * 5);
        assertEquals(afterShutdown, goffApiMock.getConfigurationCallCount(), "polling continued after shutdown()");
    }

    @SneakyThrows
    @DisplayName("should report PROVIDER_NOT_READY before any configuration is loaded")
    @Test
    void shouldReportProviderNotReadyBeforeAnyConfigurationIsLoaded() {
        val got = evaluator(this.server)
                .getBooleanEvaluation("bool_targeting_match", false, new ImmutableContext("user-key"));

        assertEquals(ErrorCode.PROVIDER_NOT_READY, got.getErrorCode());
        assertEquals(Reason.ERROR.name(), got.getReason());
        assertEquals(false, got.getValue());
    }

    @SneakyThrows
    @DisplayName("should report PROVIDER_NOT_READY, not FLAG_NOT_FOUND, after a failed init")
    @Test
    void shouldReportProviderNotReadyAfterAFailedInit() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.ENDPOINT_ERROR_404).dispatcher);
            val evaluator = evaluator(s);
            assertThrows(FlagConfigurationEndpointNotFound.class, () -> evaluator.initialize(new ImmutableContext()));

            val got = evaluator.getBooleanEvaluation("bool_targeting_match", false, new ImmutableContext("user-key"));

            assertEquals(ErrorCode.PROVIDER_NOT_READY, got.getErrorCode());
            // the flag key is not at fault here, the relay proxy is
            assertNotEquals(ErrorCode.FLAG_NOT_FOUND, got.getErrorCode());
        }
    }

    @SneakyThrows
    @DisplayName("should report FLAG_NOT_FOUND for an unknown key once a configuration is loaded")
    @Test
    void shouldReportFlagNotFoundForAnUnknownKeyOnceLoaded() {
        val evaluator = evaluator(this.server);
        evaluator.initialize(new ImmutableContext());

        assertThrows(
                FlagNotFoundError.class,
                () -> evaluator.getBooleanEvaluation("DOES_NOT_EXIST", false, new ImmutableContext("user-key")));
        evaluator.shutdown();
    }

    @SneakyThrows
    @DisplayName("an empty but valid configuration counts as loaded")
    @Test
    void anEmptyButValidConfigurationCountsAsLoaded() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.EMPTY_FLAG_CONFIG).dispatcher);
            val evaluator = evaluator(s);
            evaluator.initialize(new ImmutableContext());

            // a relay proxy legitimately serving zero flags is loaded: the key really is unknown
            assertThrows(
                    FlagNotFoundError.class,
                    () -> evaluator.getBooleanEvaluation(
                            "bool_targeting_match", false, new ImmutableContext("user-key")));
            evaluator.shutdown();
        }
    }

    @SneakyThrows
    @DisplayName("should stay loaded across shutdown and re-init")
    @Test
    void shouldStayLoadedAcrossShutdownAndReInit() {
        val evaluator = evaluator(this.server);
        evaluator.initialize(new ImmutableContext());
        evaluator.shutdown();
        evaluator.initialize(new ImmutableContext());

        // the stored ETag survives shutdown(), so a re-init answered 304 must keep serving what it holds
        assertThrows(
                FlagNotFoundError.class,
                () -> evaluator.getBooleanEvaluation("DOES_NOT_EXIST", false, new ImmutableContext("user-key")));
        evaluator.shutdown();
    }

    @SneakyThrows
    @DisplayName("a missing targeting key should be passed through to the engine")
    @Test
    void aMissingTargetingKeyShouldBePassedThroughToTheEngine() {
        val evaluator = evaluator(this.server);
        evaluator.initialize(new ImmutableContext());

        // object_key resolves through a default rule that does not bucket, so it needs no targeting key
        val got = evaluator.getObjectEvaluation("object_key", null, new ImmutableContext());

        assertNull(got.getErrorCode());
        assertEquals("varA", got.getVariant());
        evaluator.shutdown();
    }

    @SneakyThrows
    @DisplayName("the engine should report TARGETING_KEY_MISSING only for a flag that buckets")
    @Test
    void theEngineShouldReportTargetingKeyMissingOnlyForAFlagThatBuckets() {
        val evaluator = evaluator(this.server);
        evaluator.initialize(new ImmutableContext());

        val got = evaluator.getStringEvaluation("string_key", "default", new ImmutableContext());

        assertEquals(ErrorCode.TARGETING_KEY_MISSING, got.getErrorCode());
        evaluator.shutdown();
    }

    @Nested
    @DisplayName("conversion of an engine response into a resolution")
    class Conversion {
        private GoFeatureFlagResponse responseWithValue(Object value) {
            val response = new GoFeatureFlagResponse();
            response.setValue(value);
            response.setReason(Reason.TARGETING_MATCH.name());
            response.setVariationType("enabled");
            return response;
        }

        private Double doubleEvaluationOf(Object engineValue) {
            return toProviderEvaluation("test-flag", 0.0, responseWithValue(engineValue), Double.class)
                    .getValue();
        }

        @DisplayName("Should throw FlagNotFoundError when flag is not found")
        @Test
        void shouldThrowFlagNotFoundErrorWhenFlagIsNotFound() {
            val response = new GoFeatureFlagResponse();
            response.setErrorCode(ErrorCode.FLAG_NOT_FOUND.name());
            response.setErrorDetails("Flag test-flag was not found in your configuration");
            response.setValue(false);

            val exception = assertThrows(
                    FlagNotFoundError.class, () -> toProviderEvaluation("test-flag", false, response, Boolean.class));

            assertEquals("Flag test-flag was not found in your configuration", exception.getMessage());
        }

        @DisplayName("Should return error response for other error codes")
        @Test
        void shouldReturnErrorResponseForOtherErrorCodes() {
            val response = new GoFeatureFlagResponse();
            response.setErrorCode(ErrorCode.GENERAL.name());
            response.setErrorDetails("Some other error occurred");
            response.setValue(false);

            ProviderEvaluation<Boolean> result = toProviderEvaluation("test-flag", false, response, Boolean.class);

            assertEquals(ErrorCode.GENERAL, result.getErrorCode());
            assertEquals("Some other error occurred", result.getErrorMessage());
            assertEquals(Reason.ERROR.name(), result.getReason());
            assertEquals(false, result.getValue());
        }

        @DisplayName("Should map an error code the SDK does not know to GENERAL")
        @Test
        void shouldMapAnUnknownErrorCodeToGeneral() {
            val response = new GoFeatureFlagResponse();
            // FLAG_CONFIG is specific to GO Feature Flag and has no SDK equivalent
            response.setErrorCode("FLAG_CONFIG");
            response.setErrorDetails("the flag configuration is invalid");

            ProviderEvaluation<Boolean> result = toProviderEvaluation("test-flag", false, response, Boolean.class);

            assertEquals(ErrorCode.GENERAL, result.getErrorCode());
            assertEquals("the flag configuration is invalid", result.getErrorMessage());
            assertEquals(false, result.getValue());
        }

        @DisplayName("Should handle successful evaluation")
        @Test
        void shouldHandleSuccessfulEvaluation() {
            val response = new GoFeatureFlagResponse();
            response.setValue(true);
            response.setReason(Reason.TARGETING_MATCH.name());
            response.setVariationType("enabled");
            response.setErrorCode(null);

            ProviderEvaluation<Boolean> result = toProviderEvaluation("test-flag", false, response, Boolean.class);

            assertEquals(true, result.getValue());
            assertEquals(Reason.TARGETING_MATCH.name(), result.getReason());
            assertEquals("enabled", result.getVariant());
            assertNull(result.getErrorCode());
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
            assertThrows(
                    TypeMismatchError.class,
                    () -> toProviderEvaluation("test-flag", 0.0, responseWithValue(true), Double.class));
        }

        @DisplayName("Should not let a decimal number satisfy the integer resolver")
        @Test
        void shouldNotLetADecimalNumberSatisfyTheIntegerResolver() {
            assertThrows(
                    TypeMismatchError.class,
                    () -> toProviderEvaluation("test-flag", 0, responseWithValue(101.25), Integer.class));
        }

        @DisplayName("Should return the caller default and keep the engine details when the value is null")
        @Test
        void shouldReturnTheCallerDefaultAndKeepTheEngineDetailsWhenTheValueIsNull() {
            val response = responseWithValue(null);
            response.setMetadata(Map.of("description", "a flag with no value"));

            ProviderEvaluation<Boolean> result = toProviderEvaluation("test-flag", true, response, Boolean.class);

            assertEquals(true, result.getValue());
            assertEquals(Reason.DEFAULT.name(), result.getReason());
            assertNull(result.getVariant());
            assertEquals("a flag with no value", result.getFlagMetadata().getString("description"));
            assertNull(result.getErrorCode());
        }

        @DisplayName("Should not return a zero value when the value is null")
        @Test
        void shouldNotReturnAZeroValueWhenTheValueIsNull() {
            assertEquals(
                    42,
                    toProviderEvaluation("test-flag", 42, responseWithValue(null), Integer.class)
                            .getValue());
            assertEquals(
                    "caller-default",
                    toProviderEvaluation("test-flag", "caller-default", responseWithValue(null), String.class)
                            .getValue());
            assertEquals(
                    new Value("caller-default"),
                    toProviderEvaluation("test-flag", new Value("caller-default"), responseWithValue(null), Value.class)
                            .getValue());
        }
    }
}
