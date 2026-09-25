package dev.openfeature.contrib.providers.gofeatureflag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.util.GoffApiMock;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.val;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Provider data collector")
class ProviderDataCollectorTest extends AbstractGoFeatureFlagProviderTest {
    @DisplayName("Should omit events if max pending events is reached")
    @SneakyThrows
    @Test
    void shouldCallMultipleTimeTheDataCollectorIfMaxPendingEventsIsReached() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .flushIntervalMs(100L)
                .maxPendingEvents(1)
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        client.getIntegerDetails("integer_key", 1000, TestUtils.defaultEvaluationContext);
        client.getIntegerDetails("integer_key", 1000, TestUtils.defaultEvaluationContext);
        Thread.sleep(180L);
        assertEquals(2, goffAPIMock.getCollectorRequestsHistory().size());
    }

    @DisplayName("Should not send evaluation event if flag has tracking disabled")
    @SneakyThrows
    @Test
    void shouldNotSendEvaluationEventIfFlagHasTrackingDisabled() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .flushIntervalMs(100L)
                .maxPendingEvents(1)
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        client.getStringDetails("string_key", "default", TestUtils.defaultEvaluationContext);
        client.getStringDetails("string_key", "default", TestUtils.defaultEvaluationContext);
        Thread.sleep(180L);
        assertEquals(0, goffAPIMock.getCollectorRequestsHistory().size());
    }

    @DisplayName("Should not send an evaluation event for a flag the relay proxy evaluated")
    @SneakyThrows
    @Test
    void shouldNotSendAnEvaluationEventForAFlagTheRelayProxyEvaluated() {
        try (val s = new MockWebServer()) {
            val mock = new GoffApiMock(GoffApiMock.MockMode.MISCONFIGURED_FLAGS);
            s.setDispatcher(mock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .flushIntervalMs(100L)
                    .maxPendingEvents(1)
                    .endpoint(s.url("").toString())
                    .evaluationType(EvaluationType.IN_PROCESS)
                    .build());
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);

            client.getBooleanDetails("flag-with-a-broken-query", false, TestUtils.defaultEvaluationContext);
            Thread.sleep(180L);

            assertEquals(List.of("flag-with-a-broken-query"), mock.getEvaluatedFlagKeys());
            assertEquals(0, mock.getCollectorRequestsHistory().size());
        }
    }

    @DisplayName("Should send an evaluation event for a flag the engine evaluated")
    @SneakyThrows
    @Test
    void shouldSendAnEvaluationEventForAFlagTheEngineEvaluated() {
        try (val s = new MockWebServer()) {
            val mock = new GoffApiMock(GoffApiMock.MockMode.MISCONFIGURED_FLAGS);
            s.setDispatcher(mock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .flushIntervalMs(100L)
                    .maxPendingEvents(1)
                    .endpoint(s.url("").toString())
                    .evaluationType(EvaluationType.IN_PROCESS)
                    .build());
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);

            client.getBooleanDetails("healthy-flag", false, TestUtils.defaultEvaluationContext);
            Thread.sleep(180L);

            // without this the suppression above would also hold with no hook wired at all
            assertEquals(1, mock.getCollectorRequestsHistory().size());
        }
    }

    @DisplayName("Should not send an evaluation event for an untrackable flag that errors")
    @SneakyThrows
    @Test
    void shouldNotSendAnEvaluationEventForAnUntrackableFlagThatErrors() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .flushIntervalMs(100L)
                .maxPendingEvents(1)
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);

        // string_key carries trackEvents:false, and asking for it as a boolean reaches the error
        // stage rather than the after stage
        val details = client.getBooleanDetails("string_key", false, TestUtils.defaultEvaluationContext);
        Thread.sleep(180L);

        assertEquals(ErrorCode.TYPE_MISMATCH, details.getErrorCode(), "the evaluation did not reach the error stage");
        assertEquals(
                0,
                goffAPIMock.getCollectorRequestsHistory().size(),
                "an untrackable flag was recorded by the error stage");
    }

    @DisplayName("Should send an evaluation event for a trackable flag that errors")
    @SneakyThrows
    @Test
    void shouldSendAnEvaluationEventForATrackableFlagThatErrors() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .flushIntervalMs(100L)
                .maxPendingEvents(1)
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);

        val details = client.getBooleanDetails("integer_key", false, TestUtils.defaultEvaluationContext);
        Thread.sleep(180L);

        assertEquals(ErrorCode.TYPE_MISMATCH, details.getErrorCode(), "the evaluation did not reach the error stage");
        assertEquals(
                1,
                goffAPIMock.getCollectorRequestsHistory().size(),
                "the gate silenced the error stage for every flag, not only untrackable ones");
    }

    @DisplayName("Should send an evaluation event for a flag absent from the configuration")
    @SneakyThrows
    @Test
    void shouldSendAnEvaluationEventForAFlagAbsentFromTheConfiguration() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .flushIntervalMs(100L)
                .maxPendingEvents(1)
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);

        val details =
                client.getBooleanDetails("a-flag-added-since-the-last-poll", false, TestUtils.defaultEvaluationContext);
        Thread.sleep(180L);

        assertEquals(ErrorCode.FLAG_NOT_FOUND, details.getErrorCode());
        assertEquals(
                1,
                goffAPIMock.getCollectorRequestsHistory().size(),
                "a flag this provider has not polled yet went unrecorded");
    }

    @DisplayName("Should record an evaluation with no targeting key under a placeholder key")
    @SneakyThrows
    @Test
    void shouldRecordAnEvaluationWithNoTargetingKeyUnderAPlaceholderKey() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .flushIntervalMs(100L)
                .maxPendingEvents(1)
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);

        client.getIntegerDetails("integer_key", 1000, new ImmutableContext());
        Thread.sleep(180L);

        val body = Const.DESERIALIZE_OBJECT_MAPPER.readValue(goffAPIMock.getLastRequestBody(), HashMap.class);
        val events = (List<Map<String, Object>>) body.get("events");
        assertEquals(1, events.size());
        assertEquals(
                "undefined-targetingKey",
                events.get(0).get("userKey"),
                "an evaluation with no targeting key was attributed to nobody");
    }

    @DisplayName("Should not send events for remote evaluation")
    @SneakyThrows
    @Test
    void shouldResolveAValidStringFlag() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .flushIntervalMs(100L)
                .maxPendingEvents(1)
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.REMOTE)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        client.getStringDetails("string_flag", "false", TestUtils.defaultEvaluationContext);
        Thread.sleep(180L);
        assertEquals(0, goffAPIMock.getCollectorRequestsHistory().size());
    }
}
