package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Test;

public class HttpConnectorOptionsTest {


    @Test
    public void testDefaultValuesInitialization() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
            .url("https://example.com")
            .build();

        assertEquals(60, options.getPollIntervalSeconds().intValue());
        assertEquals(10, options.getConnectTimeoutSeconds().intValue());
        assertEquals(10, options.getRequestTimeoutSeconds().intValue());
        assertEquals(100, options.getLinkedBlockingQueueCapacity().intValue());
        assertEquals(2, options.getScheduledThreadPoolSize().intValue());
        assertNotNull(options.getHeaders());
        assertTrue(options.getHeaders().isEmpty());
        assertNotNull(options.getHttpClientExecutor());
        assertNull(options.getProxyHost());
        assertNull(options.getProxyPort());
        assertNull(options.getPayloadCacheOptions());
        assertNull(options.getPayloadCache());
        assertNull(options.getUseHttpCache());
        assertEquals("https://example.com", options.getUrl());
    }

    @Test
    public void testInvalidUrlFormat() {
        MalformedURLException exception = assertThrows(
            MalformedURLException.class,
            () -> dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
                .url("invalid-url")
                .build()
        );

        assertNotNull(exception);
    }

    @Test
    public void testCustomValuesInitialization() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
            .pollIntervalSeconds(120)
            .connectTimeoutSeconds(20)
            .requestTimeoutSeconds(30)
            .linkedBlockingQueueCapacity(200)
            .scheduledThreadPoolSize(5)
            .url("http://example.com")
            .build();

        assertEquals(120, options.getPollIntervalSeconds().intValue());
        assertEquals(20, options.getConnectTimeoutSeconds().intValue());
        assertEquals(30, options.getRequestTimeoutSeconds().intValue());
        assertEquals(200, options.getLinkedBlockingQueueCapacity().intValue());
        assertEquals(5, options.getScheduledThreadPoolSize().intValue());
        assertEquals("http://example.com", options.getUrl());
    }

    @Test
    public void testCustomHeadersMap() {
        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("Authorization", "Bearer token");
        customHeaders.put("Content-Type", "application/json");

        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
            .url("http://example.com")
            .headers(customHeaders)
            .build();

        assertEquals("Bearer token", options.getHeaders().get("Authorization"));
        assertEquals("application/json", options.getHeaders().get("Content-Type"));
    }

    @Test
    public void testCustomExecutorService() {
        ExecutorService customExecutor = Executors.newFixedThreadPool(5);
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
            .url("https://example.com")
            .httpClientExecutor(customExecutor)
            .build();

        assertEquals(customExecutor, options.getHttpClientExecutor());
    }

    @Test
    public void testSettingPayloadCacheWithValidOptions() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions cacheOptions = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions.builder()
            .updateIntervalSeconds(1800)
            .build();
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache payloadCache = new dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache() {
            private String payload;

            @Override
            public void put(String payload) {
                this.payload = payload;
            }

            @Override
            public String get() {
                return this.payload;
            }
        };

        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
            .url("https://example.com")
            .payloadCacheOptions(cacheOptions)
            .payloadCache(payloadCache)
            .build();

        assertNotNull(options.getPayloadCacheOptions());
        assertNotNull(options.getPayloadCache());
        assertEquals(1800, options.getPayloadCacheOptions().getUpdateIntervalSeconds());
    }

    @Test
    public void testProxyConfigurationWithValidHostAndPort() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
            .url("https://example.com")
            .proxyHost("proxy.example.com")
            .proxyPort(8080)
            .build();

        assertEquals("proxy.example.com", options.getProxyHost());
        assertEquals(8080, options.getProxyPort().intValue());
    }

    @Test
    public void testLinkedBlockingQueueCapacityOutOfRange() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
                .url("https://example.com")
                .linkedBlockingQueueCapacity(0)
                .build();
        });
        assertEquals("linkedBlockingQueueCapacity must be between 1 and 1000", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class, () -> {
            dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
                .url("https://example.com")
                .linkedBlockingQueueCapacity(1001)
                .build();
        });
        assertEquals("linkedBlockingQueueCapacity must be between 1 and 1000", exception.getMessage());
    }

    @Test
    public void testPollIntervalSecondsOutOfRange() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
                .url("https://example.com")
                .pollIntervalSeconds(700)
                .build();
        });
        assertEquals("pollIntervalSeconds must be between 1 and 600", exception.getMessage());
    }

    @Test
    public void testAdditionalCustomValuesInitialization() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token");
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions cacheOptions = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions.builder().build();
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache cache = new dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache() {
            @Override
            public void put(String payload) {
                // do nothing
            }
            @Override
            public String get() { return null; }
        };

        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
            .url("https://example.com")
            .pollIntervalSeconds(120)
            .connectTimeoutSeconds(20)
            .requestTimeoutSeconds(30)
            .linkedBlockingQueueCapacity(200)
            .scheduledThreadPoolSize(4)
            .headers(headers)
            .httpClientExecutor(executorService)
            .proxyHost("proxy.example.com")
            .proxyPort(8080)
            .payloadCacheOptions(cacheOptions)
            .payloadCache(cache)
            .useHttpCache(true)
            .build();

        assertEquals(120, options.getPollIntervalSeconds().intValue());
        assertEquals(20, options.getConnectTimeoutSeconds().intValue());
        assertEquals(30, options.getRequestTimeoutSeconds().intValue());
        assertEquals(200, options.getLinkedBlockingQueueCapacity().intValue());
        assertEquals(4, options.getScheduledThreadPoolSize().intValue());
        assertNotNull(options.getHeaders());
        assertEquals("Bearer token", options.getHeaders().get("Authorization"));
        assertNotNull(options.getHttpClientExecutor());
        assertEquals("proxy.example.com", options.getProxyHost());
        assertEquals(8080, options.getProxyPort().intValue());
        assertNotNull(options.getPayloadCacheOptions());
        assertNotNull(options.getPayloadCache());
        assertTrue(options.getUseHttpCache());
        assertEquals("https://example.com", options.getUrl());
    }

    @Test
    public void testRequestTimeoutSecondsOutOfRange() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
                .url("https://example.com")
                .requestTimeoutSeconds(61)
                .build();
        });
        assertEquals("requestTimeoutSeconds must be between 1 and 60", exception.getMessage());
    }

    @Test
    public void testBuilderInitializesAllFields() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token");
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions cacheOptions = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCacheOptions.builder().build();
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache cache = new dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache() {
            @Override
            public void put(String payload) {
                // do nothing
            }
            @Override
            public String get() { return null; }
        };

        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
            .pollIntervalSeconds(120)
            .connectTimeoutSeconds(20)
            .requestTimeoutSeconds(30)
            .linkedBlockingQueueCapacity(200)
            .scheduledThreadPoolSize(4)
            .headers(headers)
            .httpClientExecutor(executorService)
            .proxyHost("proxy.example.com")
            .proxyPort(8080)
            .payloadCacheOptions(cacheOptions)
            .payloadCache(cache)
            .useHttpCache(true)
            .url("https://example.com")
            .build();

        assertEquals(120, options.getPollIntervalSeconds().intValue());
        assertEquals(20, options.getConnectTimeoutSeconds().intValue());
        assertEquals(30, options.getRequestTimeoutSeconds().intValue());
        assertEquals(200, options.getLinkedBlockingQueueCapacity().intValue());
        assertEquals(4, options.getScheduledThreadPoolSize().intValue());
        assertEquals(headers, options.getHeaders());
        assertEquals(executorService, options.getHttpClientExecutor());
        assertEquals("proxy.example.com", options.getProxyHost());
        assertEquals(8080, options.getProxyPort().intValue());
        assertEquals(cacheOptions, options.getPayloadCacheOptions());
        assertEquals(cache, options.getPayloadCache());
        assertTrue(options.getUseHttpCache());
        assertEquals("https://example.com", options.getUrl());
    }

    @Test
    public void testScheduledThreadPoolSizeOutOfRange() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
                .url("https://example.com")
                .scheduledThreadPoolSize(11)
                .build();
        });
        assertEquals("scheduledThreadPoolSize must be between 1 and 10", exception.getMessage());
    }

    @Test
    public void testProxyPortOutOfRange() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
                .url("https://example.com")
                .proxyHost("proxy.example.com")
                .proxyPort(70000) // Invalid port, out of range
                .build();
        });
        assertEquals("proxyPort must be between 1 and 65535", exception.getMessage());
    }

    @Test
    public void testConnectTimeoutSecondsOutOfRange() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
                .url("https://example.com")
                .connectTimeoutSeconds(0)
                .build();
        });
        assertEquals("connectTimeoutSeconds must be between 1 and 60", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class, () -> {
            dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
                .url("https://example.com")
                .connectTimeoutSeconds(61)
                .build();
        });
        assertEquals("connectTimeoutSeconds must be between 1 and 60", exception.getMessage());
    }

    @Test
    public void testProxyPortWithoutProxyHost() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
                .url("https://example.com")
                .proxyPort(8080)
                .build();
        });
        assertEquals("proxyHost must be set if proxyPort is set", exception.getMessage());
    }

    @Test
    public void testDefaultValuesWhenNullParametersProvided() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions options = dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
            .url("https://example.com")
            .pollIntervalSeconds(null)
            .linkedBlockingQueueCapacity(null)
            .scheduledThreadPoolSize(null)
            .requestTimeoutSeconds(null)
            .connectTimeoutSeconds(null)
            .headers(null)
            .httpClientExecutor(null)
            .proxyHost(null)
            .proxyPort(null)
            .payloadCacheOptions(null)
            .payloadCache(null)
            .useHttpCache(null)
            .build();

        assertEquals(60, options.getPollIntervalSeconds().intValue());
        assertEquals(10, options.getConnectTimeoutSeconds().intValue());
        assertEquals(10, options.getRequestTimeoutSeconds().intValue());
        assertEquals(100, options.getLinkedBlockingQueueCapacity().intValue());
        assertEquals(2, options.getScheduledThreadPoolSize().intValue());
        assertNotNull(options.getHeaders());
        assertTrue(options.getHeaders().isEmpty());
        assertNotNull(options.getHttpClientExecutor());
        assertNull(options.getProxyHost());
        assertNull(options.getProxyPort());
        assertNull(options.getPayloadCacheOptions());
        assertNull(options.getPayloadCache());
        assertNull(options.getUseHttpCache());
        assertEquals("https://example.com", options.getUrl());
    }

    @Test
    public void testProxyHostWithoutProxyPort() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
                .url("https://example.com")
                .proxyHost("proxy.example.com")
                .build();
        });
        assertEquals("proxyPort must be set if proxyHost is set", exception.getMessage());
    }

    @Test
    public void testSettingPayloadCacheWithoutOptions() {
        dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.PayloadCache mockPayloadCache = new PayloadCache() {
            @Override
            public void put(String payload) {
                // Mock implementation
            }

            @Override
            public String get() {
                return "mockPayload";
            }
        };

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.http.HttpConnectorOptions.builder()
                .url("https://example.com")
                .payloadCache(mockPayloadCache)
                .build();
        });

        assertEquals("payloadCacheOptions must be set if payloadCache is set", exception.getMessage());
    }

    @Test
    public void testPayloadCacheOptionsWithoutPayloadCache() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            HttpConnectorOptions.builder()
                .url("https://example.com")
                .payloadCacheOptions(PayloadCacheOptions.builder().build())
                .build();
        });
        assertEquals("payloadCache must be set if payloadCacheOptions is set", exception.getMessage());
    }
}
