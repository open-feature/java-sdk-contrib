package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import dev.openfeature.sdk.EvaluationContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@Slf4j
class HttpConnectorTest {

    @SneakyThrows
    @Test
    void testGetStreamQueueInitialAndScheduledPolls() {
        String testUrl = "http://example.com";
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("test data");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        HttpConnectorOptions httpConnectorOptions = HttpConnectorOptions.builder()
            .url(testUrl)
            .httpClientExecutor(Executors.newSingleThreadExecutor())
            .build();
        HttpConnector connector = HttpConnector.builder()
            .httpConnectorOptions(httpConnectorOptions)
            .build();

        Field clientField = HttpConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);

        BlockingQueue<QueuePayload> queue = connector.getStreamQueue();

        assertFalse(queue.isEmpty());
        QueuePayload payload = queue.poll();
        assertNotNull(payload);
        assertEquals(QueuePayloadType.DATA, payload.getType());
        assertEquals("test data", payload.getFlagData());

        connector.shutdown();
    }

    @SneakyThrows
    @Test
    void testBuildPollTaskFetchesDataAndAddsToQueue() {
        String testUrl = "http://example.com";
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("test data");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        PayloadCache payloadCache = new PayloadCache() {
            private String payload;
            @Override
            public void put(String payload) {
                this.payload = payload;
            }

            @Override
            public String get() {
                return payload;
            }
        };
        HttpConnectorOptions httpConnectorOptions = HttpConnectorOptions.builder()
            .url(testUrl)
            .proxyHost("proxy-host")
            .proxyPort(8080)
            .useHttpCache(true)
            .payloadCache(payloadCache)
            .payloadCacheOptions(PayloadCacheOptions.builder().build())
            .build();
        HttpConnector connector = HttpConnector.builder()
            .httpConnectorOptions(httpConnectorOptions)
            .build();
        connector.init();

        Field clientField = HttpConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);

        Runnable pollTask = connector.buildPollTask();
        pollTask.run();

        Field queueField = HttpConnector.class.getDeclaredField("queue");
        queueField.setAccessible(true);
        BlockingQueue<QueuePayload> queue = (BlockingQueue<QueuePayload>) queueField.get(connector);
        assertFalse(queue.isEmpty());
        QueuePayload payload = queue.poll();
        assertNotNull(payload);
        assertEquals(QueuePayloadType.DATA, payload.getType());
        assertEquals("test data", payload.getFlagData());
    }

    @SneakyThrows
    @Test
    void testHttpRequestIncludesHeaders() {
        String testUrl = "http://example.com";
        Map<String, String> testHeaders = new HashMap<>();
        testHeaders.put("Authorization", "Bearer token");
        testHeaders.put("Content-Type", "application/json");

        HttpConnectorOptions httpConnectorOptions = HttpConnectorOptions.builder()
            .url(testUrl)
            .headers(testHeaders)
            .build();
        HttpConnector connector = HttpConnector.builder()
            .httpConnectorOptions(httpConnectorOptions)
            .build();

        Field headersField = HttpConnector.class.getDeclaredField("headers");
        headersField.setAccessible(true);
        Map<String, String> headers = (Map<String, String>) headersField.get(connector);
        assertNotNull(headers);
        assertEquals(2, headers.size());
        assertEquals("Bearer token", headers.get("Authorization"));
        assertEquals("application/json", headers.get("Content-Type"));
    }

    @SneakyThrows
    @Test
    void testSuccessfulHttpResponseAddsDataToQueue() {
        String testUrl = "http://example.com";
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("test data");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        HttpConnectorOptions httpConnectorOptions = HttpConnectorOptions.builder()
            .url(testUrl)
            .build();
        HttpConnector connector = HttpConnector.builder()
            .httpConnectorOptions(httpConnectorOptions)
            .build();

        Field clientField = HttpConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);

        BlockingQueue<QueuePayload> queue = connector.getStreamQueue();

        assertFalse(queue.isEmpty());
        QueuePayload payload = queue.poll();
        assertNotNull(payload);
        assertEquals(QueuePayloadType.DATA, payload.getType());
        assertEquals("test data", payload.getFlagData());
    }

    @SneakyThrows
    @Test
    void testInitFailureUsingCache() {
        String testUrl = "http://example.com";
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("Simulated IO Exception"));

        final String cachedData = "cached data";
        PayloadCache payloadCache = new PayloadCache() {
            @Override
            public void put(String payload) {
                // do nothing
            }

            @Override
            public String get() {
                return cachedData;
            }
        };

        HttpConnectorOptions httpConnectorOptions = HttpConnectorOptions.builder()
            .url(testUrl)
            .payloadCache(payloadCache)
            .payloadCacheOptions(PayloadCacheOptions.builder().build())
            .build();
        HttpConnector connector = HttpConnector.builder()
            .httpConnectorOptions(httpConnectorOptions)
            .build();

        Field clientField = HttpConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);

        BlockingQueue<QueuePayload> queue = connector.getStreamQueue();

        assertFalse(queue.isEmpty());
        QueuePayload payload = queue.poll();
        assertNotNull(payload);
        assertEquals(QueuePayloadType.DATA, payload.getType());
        assertEquals(cachedData, payload.getFlagData());
    }

    @SneakyThrows
    @Test
    void testQueueBecomesFull() {
        String testUrl = "http://example.com";
        int queueCapacity = 1;
        HttpConnectorOptions httpConnectorOptions = HttpConnectorOptions.builder()
            .url(testUrl)
            .linkedBlockingQueueCapacity(queueCapacity)
            .build();
        HttpConnector connector = HttpConnector.builder()
            .httpConnectorOptions(httpConnectorOptions)
            .build();

        BlockingQueue<QueuePayload> queue = connector.getStreamQueue();

        queue.offer(new QueuePayload(QueuePayloadType.DATA, "test data 1"));

        boolean wasOffered = queue.offer(new QueuePayload(QueuePayloadType.DATA, "test data 2"));

        assertFalse(wasOffered, "Queue should be full and not accept more items");
    }

    @SneakyThrows
    @Test
    void testShutdownProperlyTerminatesSchedulerAndHttpClientExecutor() throws InterruptedException {
        ExecutorService mockHttpClientExecutor = mock(ExecutorService.class);
        ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);
        String testUrl = "http://example.com";

        HttpConnectorOptions httpConnectorOptions = HttpConnectorOptions.builder()
            .url(testUrl)
            .httpClientExecutor(mockHttpClientExecutor)
            .build();
        HttpConnector connector = HttpConnector.builder()
            .httpConnectorOptions(httpConnectorOptions)
            .build();

        Field schedulerField = HttpConnector.class.getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        schedulerField.set(connector, mockScheduler);

        connector.shutdown();

        Mockito.verify(mockScheduler).shutdown();
        Mockito.verify(mockHttpClientExecutor).shutdown();
    }

    @SneakyThrows
    @Test
    void testHttpResponseNonSuccessStatusCode() {
        String testUrl = "http://example.com";
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(404);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        HttpConnectorOptions httpConnectorOptions = HttpConnectorOptions.builder()
            .url(testUrl)
            .build();
        HttpConnector connector = HttpConnector.builder()
            .httpConnectorOptions(httpConnectorOptions)
            .build();

        Field clientField = HttpConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);

        BlockingQueue<QueuePayload> queue = connector.getStreamQueue();

        assertTrue(queue.isEmpty(), "Queue should be empty when response status is non-200");
    }

    @SneakyThrows
    @Test
    void testHttpRequestFailsWithException() {
        String testUrl = "http://example.com";
        HttpClient mockClient = mock(HttpClient.class);
        HttpConnectorOptions httpConnectorOptions = HttpConnectorOptions.builder()
            .url(testUrl)
            .build();
        HttpConnector connector = HttpConnector.builder()
            .httpConnectorOptions(httpConnectorOptions)
            .build();

        Field clientField = HttpConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);

        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new RuntimeException("Test exception"));

        BlockingQueue<QueuePayload> queue = connector.getStreamQueue();

        assertTrue(queue.isEmpty(), "Queue should be empty when request fails with exception");
    }

    @SneakyThrows
    @Test
    void testHttpRequestFailsWithIoexception() {
        String testUrl = "http://example.com";
        HttpClient mockClient = mock(HttpClient.class);
        HttpConnectorOptions httpConnectorOptions = HttpConnectorOptions.builder()
            .url(testUrl)
            .build();
        HttpConnector connector = HttpConnector.builder()
            .httpConnectorOptions(httpConnectorOptions)
            .build();

        Field clientField = HttpConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);

        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("Simulated IO Exception"));

        connector.getStreamQueue();

        Field queueField = HttpConnector.class.getDeclaredField("queue");
        queueField.setAccessible(true);
        BlockingQueue<QueuePayload> queue = (BlockingQueue<QueuePayload>) queueField.get(connector);
        assertTrue(queue.isEmpty(), "Queue should be empty due to IOException");
    }

    @SneakyThrows
    @Test
    void testScheduledPollingContinuesAtFixedIntervals() {
        String testUrl = "http://exampOle.com";
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("test data");

        HttpConnectorOptions httpConnectorOptions = HttpConnectorOptions.builder()
            .url(testUrl)
            .build();
        HttpConnector connector = spy(HttpConnector.builder()
            .httpConnectorOptions(httpConnectorOptions)
            .build());

        doReturn(mockResponse).when(connector).execute(any());

        BlockingQueue<QueuePayload> queue = connector.getStreamQueue();

        delay(2000);
        assertFalse(queue.isEmpty());
        QueuePayload payload = queue.poll();
        assertNotNull(payload);
        assertEquals(QueuePayloadType.DATA, payload.getType());
        assertEquals("test data", payload.getFlagData());

        connector.shutdown();
    }

    @SneakyThrows
    @Test
    void testQueuePayloadTypeSetToDataOnSuccess() {
        String testUrl = "http://example.com";
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("response body");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        HttpConnectorOptions httpConnectorOptions = HttpConnectorOptions.builder()
            .url(testUrl)
            .build();
        HttpConnector connector = HttpConnector.builder()
            .httpConnectorOptions(httpConnectorOptions)
            .build();

        Field clientField = HttpConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);

        BlockingQueue<QueuePayload> queue = connector.getStreamQueue();

        QueuePayload payload = queue.poll(1, TimeUnit.SECONDS);
        assertNotNull(payload);
        assertEquals(QueuePayloadType.DATA, payload.getType());
        assertEquals("response body", payload.getFlagData());
    }

    @Test
    public void providerTest() {
        HttpConnectorOptions httpConnectorOptions = HttpConnectorOptions.builder()
            .url("http://example.com")
            .build();
        HttpConnector connector = HttpConnector.builder()
            .httpConnectorOptions(httpConnectorOptions)
            .build();

        FlagdOptions options =
            FlagdOptions.builder()
                .resolverType(Config.Resolver.IN_PROCESS)
                .customConnector(connector)
                .build();

        FlagdProvider flagdProvider = new FlagdProvider(options);

        assertDoesNotThrow(() -> flagdProvider.getMetadata());
    }

    @SneakyThrows
    protected static void delay(long ms) {
        Thread.sleep(ms);
    }

}
