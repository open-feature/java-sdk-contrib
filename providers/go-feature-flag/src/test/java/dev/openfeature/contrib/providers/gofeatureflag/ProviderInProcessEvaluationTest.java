package dev.openfeature.contrib.providers.gofeatureflag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.gofeatureflag.util.GoffApiMock;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FatalError;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import lombok.val;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Provider in-process evaluation")
class ProviderInProcessEvaluationTest extends AbstractGoFeatureFlagProviderTest {
    @DisplayName("Should use in process evaluation by default")
    @SneakyThrows
    @Test
    void shouldUseInProcessByDefault() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        client.getBooleanDetails("bool_targeting_match", false, new MutableContext());
        val want = "/v1/flag/configuration";
        assertEquals(want, server.takeRequest().getPath());
    }

    @DisplayName("Should use in process evaluation if option is set")
    @SneakyThrows
    @Test
    void shouldUseInProcessIfOptionIsSet() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        client.getBooleanDetails("bool_targeting_match", false, new MutableContext());
        val want = "/v1/flag/configuration";
        assertEquals(want, server.takeRequest().getPath());
    }

    @DisplayName("Should throw an error if the endpoint is not available")
    @SneakyThrows
    @Test
    void shouldThrowAnErrorIfEndpointNotAvailable() {
        try (val s = new MockWebServer()) {
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.ENDPOINT_ERROR);
            s.setDispatcher(goffAPIMock.dispatcher);
            GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .endpoint(s.url("").toString())
                    .timeout(1000)
                    .build());
            assertThrows(GeneralError.class, () -> OpenFeatureAPI.getInstance().setProviderAndWait(testName, g));
        }
    }

    @DisplayName("Should throw an error if api key is missing")
    @SneakyThrows
    @Test
    void shouldThrowAnErrorIfApiKeyIsMissing() {
        try (val s = new MockWebServer()) {
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.ENDPOINT_ERROR);
            s.setDispatcher(goffAPIMock.dispatcher);
            GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .endpoint(s.url("").toString())
                    .timeout(1000)
                    .build());
            assertThrows(GeneralError.class, () -> OpenFeatureAPI.getInstance().setProviderAndWait(testName, g));
        }
    }

    @DisplayName("Should return FLAG_NOT_FOUND if the flag does not exists")
    @SneakyThrows
    @Test
    void shouldReturnFlagNotFoundIfFlagDoesNotExists() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        val got = client.getBooleanDetails("DOES_NOT_EXISTS", false, TestUtils.defaultEvaluationContext);

        val want = FlagEvaluationDetails.<Boolean>builder()
                .value(false)
                .flagKey("DOES_NOT_EXISTS")
                .reason(Reason.ERROR.name())
                .errorCode(ErrorCode.FLAG_NOT_FOUND)
                .errorMessage("Flag DOES_NOT_EXISTS was not found in your configuration")
                .build();
        assertEquals(want, got);
    }

    @DisplayName("Should throw an error if we expect a boolean and got another type")
    @SneakyThrows
    @Test
    void shouldThrowAnErrorIfWeExpectABooleanAndGotAnotherType() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        val got = client.getBooleanDetails("string_key", false, TestUtils.defaultEvaluationContext);
        val want = FlagEvaluationDetails.<Boolean>builder()
                .flagKey("string_key")
                .value(false)
                .reason(Reason.ERROR.name())
                .errorCode(ErrorCode.TYPE_MISMATCH)
                .errorMessage(
                        "Flag value string_key had unexpected type class java.lang.String, expected class java.lang.Boolean.")
                .build();
        assertEquals(want, got);
    }

    @DisplayName("Should resolve a valid boolean flag with TARGETING MATCH reason")
    @SneakyThrows
    @Test
    void shouldResolveAValidBooleanFlagWithTargetingMatchReason() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        val got = client.getBooleanDetails("bool_targeting_match", false, TestUtils.defaultEvaluationContext);
        val want = FlagEvaluationDetails.<Boolean>builder()
                .value(true)
                .variant("enabled")
                .flagKey("bool_targeting_match")
                .reason(Reason.TARGETING_MATCH.name())
                .flagMetadata(ImmutableMetadata.builder()
                        .addString("description", "this is a test flag")
                        .addBoolean("defaultValue", false)
                        .build())
                .build();
        assertEquals(want, got);
    }

    @DisplayName("Should resolve a valid string flag with TARGETING MATCH reason")
    @SneakyThrows
    @Test
    void shouldResolveAValidStringFlagWithTargetingMatchReason() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        val got = client.getStringDetails("string_key", "", TestUtils.defaultEvaluationContext);
        val want = FlagEvaluationDetails.<String>builder()
                .value("CC0002")
                .variant("color1")
                .flagKey("string_key")
                .reason(Reason.STATIC.name())
                .flagMetadata(ImmutableMetadata.builder()
                        .addString("description", "this is a test flag")
                        .addString("defaultValue", "CC0000")
                        .build())
                .build();
        assertEquals(want, got);
    }

    @DisplayName("Should resolve a valid double flag with TARGETING MATCH reason")
    @SneakyThrows
    @Test
    void shouldResolveAValidDoubleFlagWithTargetingMatchReason() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        val got = client.getDoubleDetails("double_key", 100.10, TestUtils.defaultEvaluationContext);
        val want = FlagEvaluationDetails.<Double>builder()
                .value(101.25)
                .variant("medium")
                .flagKey("double_key")
                .reason(Reason.TARGETING_MATCH.name())
                .flagMetadata(ImmutableMetadata.builder()
                        .addString("description", "this is a test flag")
                        .addDouble("defaultValue", 100.25)
                        .build())
                .build();
        assertEquals(want, got);
    }

    @DisplayName("Should resolve a valid integer flag with TARGETING MATCH reason")
    @SneakyThrows
    @Test
    void shouldResolveAValidIntegerFlagWithTargetingMatchReason() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        val got = client.getIntegerDetails("integer_key", 1000, TestUtils.defaultEvaluationContext);
        val want = FlagEvaluationDetails.<Integer>builder()
                .value(101)
                .variant("medium")
                .flagKey("integer_key")
                .reason(Reason.TARGETING_MATCH.name())
                .flagMetadata(ImmutableMetadata.builder()
                        .addString("description", "this is a test flag")
                        .addInteger("defaultValue", 1000)
                        .build())
                .build();
        assertEquals(want, got);
    }

    @DisplayName("Should resolve a valid object flag with TARGETING MATCH reason")
    @SneakyThrows
    @Test
    void shouldResolveAValidObjectFlagWithTargetingMatchReason() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        val got = client.getObjectDetails(
                "object_key",
                Value.objectToValue(new MutableStructure().add("default", "true")),
                TestUtils.defaultEvaluationContext);
        val want = FlagEvaluationDetails.builder()
                .value(Value.objectToValue(new MutableStructure().add("test", "false")))
                .variant("varB")
                .flagKey("object_key")
                .reason(Reason.TARGETING_MATCH.name())
                .build();
        assertEquals(want, got);
    }

    @DisplayName("Should use boolean default value if the flag is disabled")
    @SneakyThrows
    @Test
    void shouldUseBooleanDefaultValueIfTheFlagIsDisabled() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        val got = client.getBooleanDetails("disabled_bool", false, TestUtils.defaultEvaluationContext);
        val want = FlagEvaluationDetails.<Boolean>builder()
                .value(false)
                .variant("SdkDefault")
                .flagKey("disabled_bool")
                .reason(Reason.DISABLED.name())
                .flagMetadata(ImmutableMetadata.builder()
                        .addString("description", "this is a test flag")
                        .addBoolean("defaultValue", false)
                        .build())
                .build();
        assertEquals(want, got);
    }

    @DisplayName("Should not reject an evaluation context without a targeting key")
    @SneakyThrows
    @Test
    void shouldNotRejectAnEvaluationContextWithoutATargetingKey() {
        try (val s = new MockWebServer()) {
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.DEFAULT);
            s.setDispatcher(goffAPIMock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .flagChangePollingIntervalMs(100L)
                    .endpoint(s.url("").toString())
                    .evaluationType(EvaluationType.IN_PROCESS)
                    .build());
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);

            val got = client.getObjectDetails("object_key", new Value("default"), new ImmutableContext());

            assertNull(got.getErrorCode());
            assertEquals("varA", got.getVariant());
        }
    }

    @DisplayName("Should return TARGETING_KEY_MISSING if the flag needs a targeting key to bucket")
    @SneakyThrows
    @Test
    void shouldReturnTargetingKeyMissingIfTheFlagNeedsATargetingKeyToBucket() {
        try (val s = new MockWebServer()) {
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.DEFAULT);
            s.setDispatcher(goffAPIMock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .flagChangePollingIntervalMs(100L)
                    .endpoint(s.url("").toString())
                    .evaluationType(EvaluationType.IN_PROCESS)
                    .build());
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);

            // string_key buckets on a percentage default rule, so the engine cannot evaluate it blind
            val got = client.getStringDetails("string_key", "default", new ImmutableContext());

            assertEquals(ErrorCode.TARGETING_KEY_MISSING, got.getErrorCode());
            assertEquals(Reason.ERROR.name(), got.getReason());
            assertEquals("default", got.getValue());
        }
    }

    @DisplayName("Should be in FATAL state if the relay proxy rejects the credentials")
    @SneakyThrows
    @Test
    void shouldBeInFatalStateIfTheRelayProxyRejectsTheCredentials() {
        try (val s = new MockWebServer()) {
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.API_KEY_MISSING);
            s.setDispatcher(goffAPIMock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .flagChangePollingIntervalMs(100L)
                    .endpoint(s.url("").toString())
                    .evaluationType(EvaluationType.IN_PROCESS)
                    .build());

            assertThrows(FatalError.class, () -> OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider));

            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getBooleanDetails("bool_targeting_match", false, TestUtils.defaultEvaluationContext);
            assertEquals(ErrorCode.PROVIDER_FATAL, got.getErrorCode());
        }
    }

    @DisplayName("Should report PROVIDER_NOT_READY on evaluation after a failed initialization")
    @SneakyThrows
    @Test
    void shouldReportProviderNotReadyAfterAFailedInitialization() {
        try (val s = new MockWebServer()) {
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.ENDPOINT_ERROR_404);
            s.setDispatcher(goffAPIMock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .flagChangePollingIntervalMs(100L)
                    .endpoint(s.url("").toString())
                    .evaluationType(EvaluationType.IN_PROCESS)
                    .build());
            assertThrows(GeneralError.class, () -> OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider));

            // a failed initialization leaves the provider in ERROR, which the SDK does not
            // short-circuit, so the resolver is reached and must answer for itself
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getBooleanDetails("bool_targeting_match", false, TestUtils.defaultEvaluationContext);

            assertEquals(ErrorCode.PROVIDER_NOT_READY, got.getErrorCode());
            assertEquals(Reason.ERROR.name(), got.getReason());
            assertEquals(false, got.getValue());
        }
    }

    @DisplayName("Should apply a scheduled rollout step")
    @SneakyThrows
    @Test
    void shouldApplyAScheduledRolloutStep() {
        try (val s = new MockWebServer()) {
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.SCHEDULED_ROLLOUT_FLAG_CONFIG);
            s.setDispatcher(goffAPIMock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .endpoint(s.url("").toString())
                    .evaluationType(EvaluationType.IN_PROCESS)
                    .timeout(1000)
                    .build());
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getBooleanDetails("my-flag", false, TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<Boolean>builder()
                    .value(true)
                    .variant("enabled")
                    .flagKey("my-flag")
                    .reason(Reason.TARGETING_MATCH.name())
                    .flagMetadata(ImmutableMetadata.builder()
                            .addString("description", "this is a test flag")
                            .addBoolean("defaultValue", false)
                            .build())
                    .build();
            assertEquals(want, got);
        }
    }

    @DisplayName("Should not apply a scheduled rollout step if the date is in the future")
    @SneakyThrows
    @Test
    void shouldNotApplyAScheduledRolloutStepIfTheDateIsInTheFuture() {
        try (val s = new MockWebServer()) {
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.SCHEDULED_ROLLOUT_FLAG_CONFIG);
            s.setDispatcher(goffAPIMock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .endpoint(s.url("").toString())
                    .evaluationType(EvaluationType.IN_PROCESS)
                    .timeout(1000)
                    .build());
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getBooleanDetails("my-flag-scheduled-in-future", true, TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<Boolean>builder()
                    .value(false)
                    .variant("disabled")
                    .flagKey("my-flag-scheduled-in-future")
                    .reason(Reason.STATIC.name())
                    .flagMetadata(ImmutableMetadata.builder()
                            .addString("description", "this is a test flag")
                            .addBoolean("defaultValue", false)
                            .build())
                    .build();
            assertEquals(want, got);
        }
    }

    @DisplayName("Should evaluate flags correctly under concurrent access")
    @SneakyThrows
    @Test
    void shouldEvaluateFlagsCorrectlyUnderConcurrentAccess() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .flagChangePollingIntervalMs(999999L)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);

        int threadCount = 20;
        int evaluationsPerThread = 100;
        AtomicInteger errorCount = new AtomicInteger(0);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            new Thread(() -> {
                        try {
                            startGate.await();
                            for (int i = 0; i < evaluationsPerThread; i++) {
                                FlagEvaluationDetails<Boolean> result = client.getBooleanDetails(
                                        "bool_targeting_match", false, TestUtils.defaultEvaluationContext);
                                if (result.getErrorCode() != null) {
                                    errorCount.incrementAndGet();
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            doneLatch.countDown();
                        }
                    })
                    .start();
        }

        startGate.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Threads did not finish in time");
        assertEquals(0, errorCount.get(), "Concurrent evaluations produced errors");
    }
}
