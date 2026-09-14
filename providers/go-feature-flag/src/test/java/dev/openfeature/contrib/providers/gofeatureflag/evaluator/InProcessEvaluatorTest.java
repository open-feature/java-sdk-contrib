package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.api.GoFeatureFlagApi;
import dev.openfeature.contrib.providers.gofeatureflag.util.GoffApiMock;
import java.io.IOException;
import lombok.SneakyThrows;
import lombok.val;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InProcessEvaluatorTest {
    private static final long POLLING_INTERVAL_MS = 100L;

    private MockWebServer server;
    private GoffApiMock goffApiMock;

    @BeforeEach
    void beforeEach() throws IOException {
        this.server = new MockWebServer();
        this.goffApiMock = new GoffApiMock(GoffApiMock.MockMode.DEFAULT);
        this.server.setDispatcher(goffApiMock.dispatcher);
        this.server.start();
    }

    @AfterEach
    void afterEach() throws IOException {
        this.server.close();
        this.server = null;
    }

    @SneakyThrows
    private InProcessEvaluator evaluator() {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(server.url("").toString())
                .flagChangePollingIntervalMs(POLLING_INTERVAL_MS)
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        return new InProcessEvaluator(api, options, details -> {});
    }

    @SneakyThrows
    @DisplayName("a second init should not leave a second poller running")
    @Test
    void aSecondInitShouldNotLeaveASecondPollerRunning() {
        val evaluator = evaluator();
        evaluator.init();
        evaluator.init();

        // let both a stranded poller and the live one have several chances to fire
        Thread.sleep(POLLING_INTERVAL_MS * 5);
        evaluator.destroy();

        // whatever was in flight when destroy() was called may still land, so settle first
        Thread.sleep(POLLING_INTERVAL_MS * 2);
        val afterDestroy = goffApiMock.getConfigurationCallCount();
        assertTrue(afterDestroy > 0, "the evaluator should have polled at least once");

        // a poller stranded by the second init would keep calling the API forever
        Thread.sleep(POLLING_INTERVAL_MS * 5);
        assertEquals(afterDestroy, goffApiMock.getConfigurationCallCount(), "polling continued after destroy()");
    }
}
