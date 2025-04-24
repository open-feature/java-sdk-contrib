package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

import static dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorTest.delay;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper;
import java.lang.reflect.Field;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;


public class PayloadCacheWrapperTest {

    @Test
    public void testConstructorInitializesWithValidParameters() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache mockCache = mock(dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache.class);
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions.builder()
            .updateIntervalSeconds(600)
            .build();

        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper wrapper = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper.builder()
            .payloadCache(mockCache)
            .payloadCacheOptions(options)
            .build();

        assertNotNull(wrapper);

        String testPayload = "test-payload";
        wrapper.updatePayloadIfNeeded(testPayload);
        wrapper.get();

        verify(mockCache).put(testPayload);
        verify(mockCache).get();
    }

    @Test
    public void testConstructorThrowsExceptionForInvalidInterval() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache mockCache = mock(dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache.class);
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions.builder()
            .updateIntervalSeconds(0)
            .build();

        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper.PayloadCacheWrapperBuilder payloadCacheWrapperBuilder = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper.builder()
                .payloadCache(mockCache)
                .payloadCacheOptions(options);
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            payloadCacheWrapperBuilder::build
        );

        assertEquals("pollIntervalSeconds must be larger than 0", exception.getMessage());
    }

    @Test
    public void testUpdateSkipsWhenIntervalNotPassed() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache mockCache = mock(dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache.class);
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions.builder()
            .updateIntervalSeconds(600)
            .build();
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper wrapper = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper.builder()
            .payloadCache(mockCache)
            .payloadCacheOptions(options)
            .build();

        String initialPayload = "initial-payload";
        wrapper.updatePayloadIfNeeded(initialPayload);

        String newPayload = "new-payload";
        wrapper.updatePayloadIfNeeded(newPayload);

        verify(mockCache, times(1)).put(initialPayload);
        verify(mockCache, never()).put(newPayload);
    }

    @Test
    public void testUpdatePayloadIfNeededHandlesPutException() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache mockCache = mock(dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache.class);
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions.builder()
            .updateIntervalSeconds(600)
            .build();
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper wrapper = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper.builder()
            .payloadCache(mockCache)
            .payloadCacheOptions(options)
            .build();
        String testPayload = "test-payload";

        doThrow(new RuntimeException("put exception")).when(mockCache).put(testPayload);

        wrapper.updatePayloadIfNeeded(testPayload);

        verify(mockCache).put(testPayload);
    }

    @Test
    public void testUpdatePayloadIfNeededUpdatesCacheAfterInterval() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache mockCache = mock(dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache.class);
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions.builder()
            .updateIntervalSeconds(1) // 1 second interval for quick test
            .build();
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper wrapper = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper.builder()
            .payloadCache(mockCache)
            .payloadCacheOptions(options)
            .build();

        String initialPayload = "initial-payload";
        String newPayload = "new-payload";

        wrapper.updatePayloadIfNeeded(initialPayload);
        delay(1100);
        wrapper.updatePayloadIfNeeded(newPayload);

        verify(mockCache).put(initialPayload);
        verify(mockCache).put(newPayload);
    }

    @Test
    public void testGetReturnsNullWhenCacheGetThrowsException() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache mockCache = mock(dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache.class);
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions.builder()
            .updateIntervalSeconds(600)
            .build();
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper wrapper = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper.builder()
            .payloadCache(mockCache)
            .payloadCacheOptions(options)
            .build();

        when(mockCache.get()).thenThrow(new RuntimeException("Cache get failed"));

        String result = wrapper.get();

        assertNull(result);

        verify(mockCache).get();
    }

    @Test
    public void test_get_returns_cached_payload() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache mockCache = mock(dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache.class);
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions.builder()
            .updateIntervalSeconds(600)
            .build();
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper wrapper = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper.builder()
            .payloadCache(mockCache)
            .payloadCacheOptions(options)
            .build();
        String expectedPayload = "cached-payload";
        when(mockCache.get()).thenReturn(expectedPayload);

        String actualPayload = wrapper.get();

        assertEquals(expectedPayload, actualPayload);

        verify(mockCache).get();
    }

    @Test
    public void test_first_call_updates_cache() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache mockCache = mock(dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache.class);
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions.builder()
            .updateIntervalSeconds(600)
            .build();
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper wrapper = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper.builder()
            .payloadCache(mockCache)
            .payloadCacheOptions(options)
            .build();

        String testPayload = "initial-payload";

        wrapper.updatePayloadIfNeeded(testPayload);

        verify(mockCache).put(testPayload);
    }

    @Test
    public void test_update_payload_once_within_interval() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache mockCache = mock(dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache.class);
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions.builder()
            .updateIntervalSeconds(1) // 1 second interval
            .build();
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper wrapper = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper.builder()
            .payloadCache(mockCache)
            .payloadCacheOptions(options)
            .build();

        String testPayload = "test-payload";

        wrapper.updatePayloadIfNeeded(testPayload);
        wrapper.updatePayloadIfNeeded(testPayload);

        verify(mockCache, times(1)).put(testPayload);
    }

    @SneakyThrows
    @Test
    public void test_last_update_time_ms_updated_after_successful_cache_update() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache mockCache = mock(dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache.class);
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions.builder()
            .updateIntervalSeconds(600)
            .build();
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper wrapper = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper.builder()
            .payloadCache(mockCache)
            .payloadCacheOptions(options)
            .build();
        String testPayload = "test-payload";

        wrapper.updatePayloadIfNeeded(testPayload);

        verify(mockCache).put(testPayload);

        Field lastUpdateTimeMsField = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper.class.getDeclaredField("lastUpdateTimeMs");
        lastUpdateTimeMsField.setAccessible(true);
        long lastUpdateTimeMs = (Long) lastUpdateTimeMsField.get(wrapper);

        assertTrue(System.currentTimeMillis() - lastUpdateTimeMs < 1000,
       "lastUpdateTimeMs should be updated to current time");
    }

    @Test
    public void test_update_payload_if_needed_respects_update_interval() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache mockCache = mock(PayloadCache.class);
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions options = PayloadCacheOptions.builder()
            .updateIntervalSeconds(600)
            .build();
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheWrapper wrapper = spy(PayloadCacheWrapper.builder()
            .payloadCache(mockCache)
            .payloadCacheOptions(options)
            .build());

        String testPayload = "test-payload";
        long initialTime = System.currentTimeMillis();
        long updateIntervalMs = options.getUpdateIntervalSeconds() * 1000L;

        doReturn(initialTime).when(wrapper).getCurrentTimeMillis();

        // First update should succeed
        wrapper.updatePayloadIfNeeded(testPayload);

        // Verify the payload was updated
        verify(mockCache).put(testPayload);

        // Attempt to update before interval has passed
        doReturn(initialTime + updateIntervalMs - 1).when(wrapper).getCurrentTimeMillis();
        wrapper.updatePayloadIfNeeded(testPayload);

        // Verify the payload was not updated again
        verify(mockCache, times(1)).put(testPayload);

        // Update after interval has passed
        doReturn(initialTime + updateIntervalMs + 1).when(wrapper).getCurrentTimeMillis();
        wrapper.updatePayloadIfNeeded(testPayload);

        // Verify the payload was updated again
        verify(mockCache, times(2)).put(testPayload);
    }

}
