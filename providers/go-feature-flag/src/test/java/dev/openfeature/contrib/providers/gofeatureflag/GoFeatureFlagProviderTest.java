package dev.openfeature.contrib.providers.gofeatureflag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.gofeatureflag.hook.DataCollectorHook;
import dev.openfeature.contrib.providers.gofeatureflag.hook.EnrichEvaluationContextHook;
import dev.openfeature.contrib.providers.gofeatureflag.util.GoffApiMock;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.MutableTrackingEventDetails;
import dev.openfeature.sdk.OpenFeatureAPI;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.val;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Provider lifecycle")
class GoFeatureFlagProviderTest extends AbstractGoFeatureFlagProviderTest {
    @DisplayName("Should stop polling the flag configuration on shutdown")
    @SneakyThrows
    @Test
    void shouldStopPollingTheFlagConfigurationOnShutdown() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .flagChangePollingIntervalMs(100L)
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        Thread.sleep(300L);

        provider.shutdown();
        // whatever was in flight when shutdown was called may still land, so settle first
        Thread.sleep(200L);
        val afterShutdown = goffAPIMock.getConfigurationCallCount();
        Thread.sleep(500L);

        assertTrue(afterShutdown > 1, "the provider never polled, so stopping is not what is under test");
        assertEquals(
                afterShutdown,
                goffAPIMock.getConfigurationCallCount(),
                "the configuration was still being polled after shutdown");
    }

    @DisplayName("Should stop the event publisher on shutdown in every mode")
    @ParameterizedTest(name = "{0} evaluation, data collection disabled: {1}")
    @MethodSource("shutdownModes")
    @SneakyThrows
    void shouldStopTheEventPublisherOnShutdownInEveryMode(EvaluationType type, boolean disableDataCollection) {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .flushIntervalMs(100L)
                .maxPendingEvents(1000)
                .endpoint(baseUrl.toString())
                .evaluationType(type)
                .disableDataCollection(disableDataCollection)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        client.track("before-shutdown", TestUtils.defaultEvaluationContext, new MutableTrackingEventDetails());

        provider.shutdown();
        val afterShutdown = goffAPIMock.getCollectorRequestsHistory().size();

        client.track("after-shutdown", TestUtils.defaultEvaluationContext, new MutableTrackingEventDetails());
        Thread.sleep(400L);

        assertEquals(1, afterShutdown, "the final drain did not happen");
        assertEquals(
                afterShutdown,
                goffAPIMock.getCollectorRequestsHistory().size(),
                "the publisher kept accepting and flushing after shutdown");
    }

    @DisplayName("Should not register the hooks twice when initialized twice")
    @SneakyThrows
    @Test
    void shouldNotRegisterTheHooksTwiceWhenInitializedTwice() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        provider.initialize(new ImmutableContext());
        val afterFirst = provider.getProviderHooks().size();
        provider.initialize(new ImmutableContext());
        val afterSecond = provider.getProviderHooks();
        provider.shutdown();

        assertEquals(afterFirst, afterSecond.size(), "a second initialization registered the hooks again");
        assertEquals(
                List.of(EnrichEvaluationContextHook.class, DataCollectorHook.class),
                afterSecond.stream().map(Object::getClass).collect(Collectors.toList()));
    }

    @DisplayName("Should record one evaluation once after being initialized twice")
    @SneakyThrows
    @Test
    void shouldRecordOneEvaluationOnceAfterBeingInitializedTwice() {
        try (val s = new MockWebServer()) {
            val mock = new GoffApiMock(GoffApiMock.MockMode.DEFAULT);
            s.setDispatcher(mock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .flushIntervalMs(100L)
                    .maxPendingEvents(1)
                    .endpoint(s.url("").toString())
                    .evaluationType(EvaluationType.IN_PROCESS)
                    .build());
            provider.initialize(new ImmutableContext());
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);

            client.getIntegerDetails("integer_key", 1000, TestUtils.defaultEvaluationContext);
            Thread.sleep(180L);
            assertEquals(
                    1,
                    mock.getCollectorRequestsHistory().size(),
                    "a duplicated data collector hook records the same evaluation once per copy");
        }
    }

    @SneakyThrows
    @Test
    void getMetadata_validate_name() {
        assertEquals(
                "GO Feature Flag Provider",
                new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                                .endpoint("https://gofeatureflag.org")
                                .timeout(1000)
                                .build())
                        .getMetadata()
                        .getName());
    }
}
