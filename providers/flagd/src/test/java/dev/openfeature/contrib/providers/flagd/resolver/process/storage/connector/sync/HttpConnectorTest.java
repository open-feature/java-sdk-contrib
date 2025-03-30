package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@Slf4j
class HttpConnectorTest {

    @SneakyThrows
    @Test
    void testConstructorInitializesDefaultValues() {
        String testUrl = "http://example.com";
        HttpConnector connector = HttpConnector.builder()
            .url(testUrl)
            .build();

        Field pollIntervalField = HttpConnector.class.getDeclaredField("pollIntervalSeconds");
        pollIntervalField.setAccessible(true);
        assertEquals(60, pollIntervalField.get(connector));

        Field requestTimeoutField = HttpConnector.class.getDeclaredField("requestTimeoutSeconds");
        requestTimeoutField.setAccessible(true);
        assertEquals(10, requestTimeoutField.get(connector));

        Field queueField = HttpConnector.class.getDeclaredField("queue");
        queueField.setAccessible(true);
        BlockingQueue<QueuePayload> queue = (BlockingQueue<QueuePayload>) queueField.get(connector);
        assertEquals(100, queue.remainingCapacity() + queue.size());

        Field headersField = HttpConnector.class.getDeclaredField("headers");
        headersField.setAccessible(true);
        Map<String, String> headers = (Map<String, String>) headersField.get(connector);
        assertNotNull(headers);
        assertTrue(headers.isEmpty());
    }

    @SneakyThrows
    @Test
    void testConstructorValidationRejectsInvalidParameters() {
        String testUrl = "http://example.com";

        HttpConnector.HttpConnectorBuilder builder3 = HttpConnector.builder()
            .url(testUrl)
            .pollIntervalSeconds(0);
        IllegalArgumentException pollIntervalException = assertThrows(
            IllegalArgumentException.class,
            builder3::build
        );
        assertEquals("pollIntervalSeconds must be between 1 and 600", pollIntervalException.getMessage());

        HttpConnector.HttpConnectorBuilder builder2 = HttpConnector.builder()
            .url(testUrl)
            .linkedBlockingQueueCapacity(1001);
        IllegalArgumentException queueCapacityException = assertThrows(
            IllegalArgumentException.class,
                builder2::build
        );
        assertEquals("linkedBlockingQueueCapacity must be between 1 and 1000", queueCapacityException.getMessage());

        HttpConnector.HttpConnectorBuilder builder1 = HttpConnector.builder()
            .url(testUrl)
            .scheduledThreadPoolSize(11);
        IllegalArgumentException threadPoolException = assertThrows(
            IllegalArgumentException.class,
            builder1::build
        );
        assertEquals("scheduledThreadPoolSize must be between 1 and 10", threadPoolException.getMessage());

        HttpConnector.HttpConnectorBuilder builder = HttpConnector.builder()
                .url(testUrl)
                .proxyHost("localhost");
        IllegalArgumentException proxyException = assertThrows(
            IllegalArgumentException.class,
                builder::build
        );
        assertEquals("proxyPort must be set if proxyHost is set", proxyException.getMessage());
    }

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

        HttpConnector connector = HttpConnector.builder()
            .url(testUrl)
            .httpClientExecutor(Executors.newSingleThreadExecutor())
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

        HttpConnector connector = HttpConnector.builder()
            .url(testUrl)
            .httpClientExecutor(Executors.newFixedThreadPool(1))
            .build();

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

        HttpConnector connector = HttpConnector.builder()
            .url(testUrl)
            .headers(testHeaders)
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
    void testConstructorInitializesWithProvidedValues() {
        Integer pollIntervalSeconds = 120;
        Integer linkedBlockingQueueCapacity = 200;
        Integer scheduledThreadPoolSize = 2;
        Integer requestTimeoutSeconds = 20;
        Integer connectTimeoutSeconds = 15;
        String url = "http://example.com";
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token");
        ExecutorService httpClientExecutor = Executors.newFixedThreadPool(2);
        String proxyHost = "proxy.example.com";
        Integer proxyPort = 8080;

        HttpConnector connector = HttpConnector.builder()
            .pollIntervalSeconds(pollIntervalSeconds)
            .linkedBlockingQueueCapacity(linkedBlockingQueueCapacity)
            .scheduledThreadPoolSize(scheduledThreadPoolSize)
            .requestTimeoutSeconds(requestTimeoutSeconds)
            .connectTimeoutSeconds(connectTimeoutSeconds)
            .url(url)
            .headers(headers)
            .httpClientExecutor(httpClientExecutor)
            .proxyHost(proxyHost)
            .proxyPort(proxyPort)
            .build();

        Field pollIntervalField = HttpConnector.class.getDeclaredField("pollIntervalSeconds");
        pollIntervalField.setAccessible(true);
        assertEquals(pollIntervalSeconds, pollIntervalField.get(connector));

        Field requestTimeoutField = HttpConnector.class.getDeclaredField("requestTimeoutSeconds");
        requestTimeoutField.setAccessible(true);
        assertEquals(requestTimeoutSeconds, requestTimeoutField.get(connector));

        Field queueField = HttpConnector.class.getDeclaredField("queue");
        queueField.setAccessible(true);
        BlockingQueue<QueuePayload> queue = (BlockingQueue<QueuePayload>) queueField.get(connector);
        assertEquals(linkedBlockingQueueCapacity, queue.remainingCapacity() + queue.size());

        Field headersField = HttpConnector.class.getDeclaredField("headers");
        headersField.setAccessible(true);
        Map<String, String> actualHeaders = (Map<String, String>) headersField.get(connector);
        assertEquals(headers, actualHeaders);

        Field urlField = HttpConnector.class.getDeclaredField("url");
        urlField.setAccessible(true);
        assertEquals(url, urlField.get(connector));
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

        HttpConnector connector = HttpConnector.builder()
            .url(testUrl)
            .httpClientExecutor(Executors.newSingleThreadExecutor())
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
    void testQueueBecomesFull() {
        String testUrl = "http://example.com";
        int queueCapacity = 1;
        HttpConnector connector = HttpConnector.builder()
                .url(testUrl)
                .linkedBlockingQueueCapacity(queueCapacity)
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

        HttpConnector connector = HttpConnector.builder()
            .url(testUrl)
            .httpClientExecutor(mockHttpClientExecutor)
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

        HttpConnector connector = HttpConnector.builder()
            .url(testUrl)
            .httpClientExecutor(Executors.newSingleThreadExecutor())
            .build();

        Field clientField = HttpConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);

        BlockingQueue<QueuePayload> queue = connector.getStreamQueue();

        assertTrue(queue.isEmpty(), "Queue should be empty when response status is non-200");
    }

    @SneakyThrows
    @Test
    void test_constructor_handles_proxy_configuration() {
        String testUrl = "http://example.com";
        String proxyHost = "proxy.example.com";
        int proxyPort = 8080;

        HttpConnector connectorWithProxy = HttpConnector.builder()
            .url(testUrl)
            .proxyHost(proxyHost)
            .proxyPort(proxyPort)
            .build();

        HttpConnector connectorWithoutProxy = HttpConnector.builder()
            .url(testUrl)
            .build();

        Field clientFieldWithProxy = HttpConnector.class.getDeclaredField("client");
        clientFieldWithProxy.setAccessible(true);
        HttpClient clientWithProxy = (HttpClient) clientFieldWithProxy.get(connectorWithProxy);
        assertNotNull(clientWithProxy);

        Field clientFieldWithoutProxy = HttpConnector.class.getDeclaredField("client");
        clientFieldWithoutProxy.setAccessible(true);
        HttpClient clientWithoutProxy = (HttpClient) clientFieldWithoutProxy.get(connectorWithoutProxy);
        assertNotNull(clientWithoutProxy);

        Optional<ProxySelector> proxySelectorWithProxy = clientWithProxy.proxy();
        assertNotNull(proxySelectorWithProxy.get());
    }

    @SneakyThrows
    @Test
    void testHttpRequestFailsWithException() {
        String testUrl = "http://example.com";
        HttpClient mockClient = mock(HttpClient.class);
        HttpConnector connector = HttpConnector.builder()
            .url(testUrl)
            .httpClientExecutor(Executors.newSingleThreadExecutor())
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
        HttpConnector connector = HttpConnector.builder()
            .url(testUrl)
            .httpClientExecutor(Executors.newSingleThreadExecutor())
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
    void testMalformedUrlThrowsException() {
        String malformedUrl = "htp://invalid-url";

        assertThrows(MalformedURLException.class, () -> {
            HttpConnector.builder()
                .url(malformedUrl)
                .build();
        });
    }

    @SneakyThrows
    @Test
    void testHeadersInitializationWhenNull() {
        String testUrl = "http://example.com";

        HttpConnector connector = HttpConnector.builder()
            .url(testUrl)
            .headers(null)
            .build();

        Field headersField = HttpConnector.class.getDeclaredField("headers");
        headersField.setAccessible(true);
        Map<String, String> headers = (Map<String, String>) headersField.get(connector);
        assertNotNull(headers);
        assertTrue(headers.isEmpty());
    }

    @SneakyThrows
    @Test
    void testScheduledPollingContinuesAtFixedIntervals() {
        String testUrl = "http://exampOle.com";
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("test data");

        HttpConnector connector = spy(HttpConnector.builder()
            .url(testUrl)
            .httpClientExecutor(Executors.newSingleThreadExecutor())
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
    void testDefaultValuesWhenOptionalParametersAreNull() {
        String testUrl = "http://example.com";

        HttpConnector connector = HttpConnector.builder()
            .url(testUrl)
            .build();

        Field pollIntervalField = HttpConnector.class.getDeclaredField("pollIntervalSeconds");
        pollIntervalField.setAccessible(true);
        assertEquals(60, pollIntervalField.get(connector));

        Field requestTimeoutField = HttpConnector.class.getDeclaredField("requestTimeoutSeconds");
        requestTimeoutField.setAccessible(true);
        assertEquals(10, requestTimeoutField.get(connector));

        Field queueField = HttpConnector.class.getDeclaredField("queue");
        queueField.setAccessible(true);
        BlockingQueue<QueuePayload> queue = (BlockingQueue<QueuePayload>) queueField.get(connector);
        assertEquals(100, queue.remainingCapacity() + queue.size());

        Field headersField = HttpConnector.class.getDeclaredField("headers");
        headersField.setAccessible(true);
        Map<String, String> headers = (Map<String, String>) headersField.get(connector);
        assertNotNull(headers);
        assertTrue(headers.isEmpty());

        Field httpClientExecutorField = HttpConnector.class.getDeclaredField("httpClientExecutor");
        httpClientExecutorField.setAccessible(true);
        ExecutorService httpClientExecutor = (ExecutorService) httpClientExecutorField.get(connector);
        assertNotNull(httpClientExecutor);
    }

    @SneakyThrows
    @Test
    void testQueuePayloadTypeSetToDataOnSuccess() {
        String testUrl = "http://example.com";
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        ExecutorService mockExecutor = Executors.newFixedThreadPool(1);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("response body");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        HttpConnector connector = HttpConnector.builder()
            .url(testUrl)
            .httpClientExecutor(mockExecutor)
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

    @SneakyThrows
    private static void delay(long ms) {
        Thread.sleep(ms);
    }

}
