package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

import static dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http.HttpConnectorTest.delay;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import java.util.concurrent.BlockingQueue;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

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
            String testUrl =
                    "https://raw.githubusercontent.com/open-feature/java-sdk-contrib/main/tools/flagd-http-connector/src/test/resources/testing-flags.json";

            HttpConnectorOptions httpConnectorOptions = HttpConnectorOptions.builder()
                    .url(testUrl)
                    .connectTimeoutSeconds(10)
                    .requestTimeoutSeconds(10)
                    .useHttpCache(true)
                    .pollIntervalSeconds(5)
                    .build();
            connector = HttpConnector.builder()
                    .httpConnectorOptions(httpConnectorOptions)
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
