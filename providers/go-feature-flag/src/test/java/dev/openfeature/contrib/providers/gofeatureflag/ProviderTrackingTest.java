package dev.openfeature.contrib.providers.gofeatureflag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.sdk.MutableTrackingEventDetails;
import dev.openfeature.sdk.OpenFeatureAPI;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Provider tracking")
class ProviderTrackingTest extends AbstractGoFeatureFlagProviderTest {
    @DisplayName("Should send the evaluation information to the data collector")
    @SneakyThrows
    @Test
    void shouldSendTrackingEventToTheDataCollector() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .flushIntervalMs(100L)
                .maxPendingEvents(1000)
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        client.track(
                "my-key",
                TestUtils.defaultEvaluationContext,
                new MutableTrackingEventDetails().add("revenue", 123).add("user_id", "123ABC"));
        Thread.sleep(200L);
        assertEquals(1, goffAPIMock.getCollectorRequestsHistory().size());
    }

    @DisplayName("Should flush buffered events on shutdown in every mode")
    @ParameterizedTest(name = "{0} evaluation, data collection disabled: {1}")
    @MethodSource("shutdownModes")
    @SneakyThrows
    void shouldFlushBufferedEventsOnShutdownInEveryMode(EvaluationType type, boolean disableDataCollection) {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                // long enough that only shutdown can flush within the test
                .flushIntervalMs(60000L)
                .maxPendingEvents(1000)
                .endpoint(baseUrl.toString())
                .evaluationType(type)
                .disableDataCollection(disableDataCollection)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        client.track("my-key", TestUtils.defaultEvaluationContext, new MutableTrackingEventDetails());
        assertEquals(0, goffAPIMock.getCollectorRequestsHistory().size(), "the event was flushed before shutdown");

        provider.shutdown();

        assertEquals(
                1,
                goffAPIMock.getCollectorRequestsHistory().size(),
                "shutdown dropped the events the publisher was holding");
    }

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
        client.track(
                "my-key",
                TestUtils.defaultEvaluationContext,
                new MutableTrackingEventDetails().add("revenue", 123).add("user_id", "123ABC"));
        client.track(
                "my-key",
                TestUtils.defaultEvaluationContext,
                new MutableTrackingEventDetails().add("revenue", 567).add("user_id", "123ABC"));
        Thread.sleep(180L);
        assertEquals(2, goffAPIMock.getCollectorRequestsHistory().size());
    }
}
