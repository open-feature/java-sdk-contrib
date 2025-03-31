package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync;

import static dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.HttpConnectorTest.delay;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
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

/**
 * Integration test for the HttpConnector class, specifically testing the ability to fetch
 * raw content from a GitHub URL. This test assumes that integration tests are enabled
 * and verifies that the HttpConnector can successfully enqueue data from the specified URL.
 * The test initializes the HttpConnector with specific configurations, waits for data
 * to be enqueued, and asserts the expected queue size. The connector is shut down
 * gracefully after the test execution.
 * As this integration test using external request, it is disabled by default, and not part of the CI build.
 */
@Slf4j
class HttpConnectorIntegrationTest {

    @SneakyThrows
    @Test
    void testGithubRawContent() {
        assumeTrue(parseBoolean("integrationTestsEnabled"));
        HttpConnector connector = null;
        try {
            String testUrl = "https://raw.githubusercontent.com/open-feature/java-sdk-contrib/58fe5da7d4e2f6f4ae2c1caf3411a01e84a1dc1a/providers/flagd/version.txt";
            connector = HttpConnector.builder()
                .url(testUrl)
                .connectTimeoutSeconds(10)
                .requestTimeoutSeconds(10)
                .useHttpCache(true)
                .pollIntervalSeconds(5)
                .build();
            BlockingQueue<QueuePayload> queue = connector.getStreamQueue();
            delay(20000);
            assertEquals(1, queue.size());
        } finally {
            if (connector != null) {
                connector.shutdown();
            }
        }
    }

    public static boolean parseBoolean(String key) {
        return Boolean.parseBoolean(System.getProperty(key, System.getenv(key)));
    }

}
