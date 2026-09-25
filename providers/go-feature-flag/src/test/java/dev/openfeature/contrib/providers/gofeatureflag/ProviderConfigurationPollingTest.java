package dev.openfeature.contrib.providers.gofeatureflag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.gofeatureflag.util.GoffApiMock;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import lombok.val;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Provider configuration polling")
class ProviderConfigurationPollingTest extends AbstractGoFeatureFlagProviderTest {
    @DisplayName("Should emit configuration change event, if config has changed")
    @SneakyThrows
    @Test
    void shouldEmitConfigurationChangeEventIfConfigHasChanged() {
        val s = new MockWebServer();
        val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.CHANGE_CONFIG_AFTER_1ST_EVAL);
        s.setDispatcher(goffAPIMock.dispatcher);
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .flagChangePollingIntervalMs(100L)
                .endpoint(s.url("").toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);

        AtomicBoolean configurationChangedCalled = new AtomicBoolean(false);
        List<String> flagsChanged = new ArrayList<>();
        client.onProviderConfigurationChanged(event -> {
            configurationChangedCalled.set(true);
            flagsChanged.addAll(event.getFlagsChanged());
        });
        client.getBooleanDetails("disabled_bool", false, TestUtils.defaultEvaluationContext);

        // waiting to get a flag change
        int maxWait = 10;
        while (!configurationChangedCalled.get() && maxWait > 0) {
            maxWait--;
            Thread.sleep(10L);
        }
        assertTrue(configurationChangedCalled.get());
        assertEquals(List.of("bool_targeting_match", "new-flag-changed", "disabled_bool"), flagsChanged);
    }

    @DisplayName("Should keep polling the configuration after a 304 not-modified response")
    @SneakyThrows
    @Test
    void shouldKeepPollingAfterANotModifiedResponse() {
        try (val s = new MockWebServer()) {
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.NOT_MODIFIED_THEN_CHANGE);
            s.setDispatcher(goffAPIMock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .flagChangePollingIntervalMs(100L)
                    .endpoint(s.url("").toString())
                    .evaluationType(EvaluationType.IN_PROCESS)
                    .build());
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);

            AtomicBoolean configurationChangedCalled = new AtomicBoolean(false);
            client.onProviderConfigurationChanged(event -> configurationChangedCalled.set(true));

            val before = client.getBooleanDetails("bool_targeting_match", false, TestUtils.defaultEvaluationContext);

            // the 1st poll answers a 304, so the change can only arrive on a later poll
            int maxWait = 200;
            while (!configurationChangedCalled.get() && maxWait > 0) {
                maxWait--;
                Thread.sleep(10L);
            }

            assertTrue(
                    configurationChangedCalled.get(),
                    "the polling daemon must survive a 304 and pick up the next configuration change");
            assertTrue(goffAPIMock.getConfigurationCallCount() >= 3, "the 304 poll should have happened");
            val after = client.getBooleanDetails("bool_targeting_match", false, TestUtils.defaultEvaluationContext);
            assertNotEquals(before, after);
        }
    }

    @DisplayName("Should not emit configuration change event, if config has not changed")
    @SneakyThrows
    @Test
    void shouldNotEmitConfigurationChangeEventIfConfigHasNotChanged() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .flagChangePollingIntervalMs(100L)
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());

        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        AtomicBoolean configurationChangedCalled = new AtomicBoolean(false);
        client.onProviderConfigurationChanged(event -> {
            configurationChangedCalled.set(true);
        });
        client.getBooleanDetails("disabled_bool", false, TestUtils.defaultEvaluationContext);
        Thread.sleep(150L);
        assertFalse(configurationChangedCalled.get());
    }

    @DisplayName("Should not emit configuration change event, if only the ETag has changed")
    @SneakyThrows
    @Test
    void shouldNotEmitConfigurationChangeEventIfOnlyTheEtagHasChanged() {
        try (val s = new MockWebServer()) {
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.SAME_CONFIG_CHANGING_ETAG);
            s.setDispatcher(goffAPIMock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .flagChangePollingIntervalMs(100L)
                    .endpoint(s.url("").toString())
                    .evaluationType(EvaluationType.IN_PROCESS)
                    .build());
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            AtomicInteger changeEvents = new AtomicInteger();
            client.onProviderConfigurationChanged(event -> changeEvents.incrementAndGet());

            Thread.sleep(500L);

            // every poll carries a validator the provider has never stored, so the ETag cannot
            // tell "changed" from "fetched" and only the content can
            assertTrue(goffAPIMock.getConfigurationCallCount() >= 3, "the provider should have polled");
            assertEquals(0, changeEvents.get(), "an unchanged configuration was announced as a change");
        }
    }

    @DisplayName("Should change evaluation details if config has changed")
    @SneakyThrows
    @Test
    void shouldChangeEvaluationValueIfConfigHasChanged() {
        val s = new MockWebServer();
        val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.CHANGE_CONFIG_AFTER_1ST_EVAL);
        s.setDispatcher(goffAPIMock.dispatcher);
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .flagChangePollingIntervalMs(100L)
                .endpoint(s.url("").toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        AtomicBoolean configurationChangedCalled = new AtomicBoolean(false);
        client.onProviderConfigurationChanged(event -> {
            configurationChangedCalled.set(true);
        });

        val got1 = client.getBooleanDetails("bool_targeting_match", false, TestUtils.defaultEvaluationContext);
        // waiting to get a flag change
        int maxWait = 10;
        while (!configurationChangedCalled.get() && maxWait > 0) {
            maxWait--;
            Thread.sleep(10L);
        }
        val got2 = client.getBooleanDetails("bool_targeting_match", false, TestUtils.defaultEvaluationContext);
        assertNotEquals(got1, got2);
    }

    @DisplayName("Should error if flag configuration endpoint return a 404")
    @SneakyThrows
    @Test
    void shouldErrorIfFlagConfigurationEndpointReturn404() {
        val s = new MockWebServer();
        val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.ENDPOINT_ERROR_404);
        s.setDispatcher(goffAPIMock.dispatcher);
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .flagChangePollingIntervalMs(100L)
                .endpoint(s.url("").toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        assertThrows(GeneralError.class, () -> OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider));
    }

    @DisplayName("Should ignore configuration if etag is different by last-modified is older")
    @SneakyThrows
    @Test
    void shouldIgnoreConfigurationIfEtagIsDifferentByLastModifiedIsOlder() {
        val s = new MockWebServer();
        val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.SERVE_OLD_CONFIGURATION);
        s.setDispatcher(goffAPIMock.dispatcher);
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .flagChangePollingIntervalMs(100L)
                .endpoint(s.url("").toString())
                .evaluationType(EvaluationType.IN_PROCESS)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        AtomicBoolean configurationChangedCalled = new AtomicBoolean(false);
        client.onProviderConfigurationChanged(event -> {
            configurationChangedCalled.set(true);
        });

        client.getBooleanDetails("bool_targeting_match", false, TestUtils.defaultEvaluationContext);
        Thread.sleep(300L);
        assertFalse(configurationChangedCalled.get());
    }
}
