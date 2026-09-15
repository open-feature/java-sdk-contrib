package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.api.GoFeatureFlagApi;
import dev.openfeature.contrib.providers.gofeatureflag.exception.FlagConfigurationEndpointNotFound;
import dev.openfeature.contrib.providers.gofeatureflag.util.GoffApiMock;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Reason;
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
    private InProcessEvaluator evaluator(MockWebServer srv) {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(srv.url("").toString())
                .flagChangePollingIntervalMs(POLLING_INTERVAL_MS)
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        return new InProcessEvaluator(api, options, details -> {});
    }

    @SneakyThrows
    @DisplayName("a second init should not leave a second poller running")
    @Test
    void aSecondInitShouldNotLeaveASecondPollerRunning() {
        val evaluator = evaluator(this.server);
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

    @SneakyThrows
    @DisplayName("should report PROVIDER_NOT_READY before any configuration is loaded")
    @Test
    void shouldReportProviderNotReadyBeforeAnyConfigurationIsLoaded() {
        val got = evaluator(this.server).evaluate("bool_targeting_match", false, new ImmutableContext("user-key"));

        assertEquals(ErrorCode.PROVIDER_NOT_READY.name(), got.getErrorCode());
        assertEquals(Reason.ERROR.name(), got.getReason());
        assertEquals(false, got.getValue());
    }

    @SneakyThrows
    @DisplayName("should report PROVIDER_NOT_READY, not FLAG_NOT_FOUND, after a failed init")
    @Test
    void shouldReportProviderNotReadyAfterAFailedInit() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.ENDPOINT_ERROR_404).dispatcher);
            val evaluator = evaluator(s);
            assertThrows(FlagConfigurationEndpointNotFound.class, evaluator::init);

            val got = evaluator.evaluate("bool_targeting_match", false, new ImmutableContext("user-key"));

            assertEquals(ErrorCode.PROVIDER_NOT_READY.name(), got.getErrorCode());
            // the flag key is not at fault here, the relay proxy is
            assertNotEquals(ErrorCode.FLAG_NOT_FOUND.name(), got.getErrorCode());
        }
    }

    @SneakyThrows
    @DisplayName("should report FLAG_NOT_FOUND for an unknown key once a configuration is loaded")
    @Test
    void shouldReportFlagNotFoundForAnUnknownKeyOnceLoaded() {
        val evaluator = evaluator(this.server);
        evaluator.init();

        val got = evaluator.evaluate("DOES_NOT_EXIST", false, new ImmutableContext("user-key"));

        assertEquals(ErrorCode.FLAG_NOT_FOUND.name(), got.getErrorCode());
        evaluator.destroy();
    }

    @SneakyThrows
    @DisplayName("an empty but valid configuration counts as loaded")
    @Test
    void anEmptyButValidConfigurationCountsAsLoaded() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.EMPTY_FLAG_CONFIG).dispatcher);
            val evaluator = evaluator(s);
            evaluator.init();

            val got = evaluator.evaluate("bool_targeting_match", false, new ImmutableContext("user-key"));

            // a relay proxy legitimately serving zero flags is loaded: the key really is unknown
            assertEquals(ErrorCode.FLAG_NOT_FOUND.name(), got.getErrorCode());
            evaluator.destroy();
        }
    }

    @SneakyThrows
    @DisplayName("should stay loaded across destroy and re-init")
    @Test
    void shouldStayLoadedAcrossDestroyAndReInit() {
        val evaluator = evaluator(this.server);
        evaluator.init();
        evaluator.destroy();
        evaluator.init();

        val got = evaluator.evaluate("DOES_NOT_EXIST", false, new ImmutableContext("user-key"));

        // the stored ETag survives destroy(), so a re-init answered 304 must keep serving what it holds
        assertEquals(ErrorCode.FLAG_NOT_FOUND.name(), got.getErrorCode());
        evaluator.destroy();
    }
}
