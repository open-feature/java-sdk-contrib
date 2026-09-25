package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import static dev.openfeature.contrib.providers.gofeatureflag.evaluator.InProcessEvaluator.toProviderEvaluation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.api.GoFeatureFlagApi;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.exception.FlagConfigurationEndpointNotFound;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.util.GoffApiMock;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.InvalidContextError;
import dev.openfeature.sdk.exceptions.OpenFeatureError;
import dev.openfeature.sdk.exceptions.ParseError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import dev.openfeature.sdk.exceptions.TargetingKeyMissingError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.val;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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
    @DisplayName("a second init should not leave a second poller running")
    @Test
    void aSecondInitShouldNotLeaveASecondPollerRunning() {
        val evaluator = evaluator(this.server);
        evaluator.initialize(new ImmutableContext());
        evaluator.initialize(new ImmutableContext());

        // let both a stranded poller and the live one have several chances to fire
        Thread.sleep(POLLING_INTERVAL_MS * 5);
        evaluator.shutdown();

        // whatever was in flight when shutdown() was called may still land, so settle first
        Thread.sleep(POLLING_INTERVAL_MS * 2);
        val afterShutdown = goffApiMock.getConfigurationCallCount();
        assertTrue(afterShutdown > 0, "the evaluator should have polled at least once");

        // a poller stranded by the second init would keep calling the API forever
        Thread.sleep(POLLING_INTERVAL_MS * 5);
        assertEquals(afterShutdown, goffApiMock.getConfigurationCallCount(), "polling continued after shutdown()");
    }

    @SneakyThrows
    @DisplayName("polling should survive an error raised while applying a configuration")
    @Test
    void pollingShouldSurviveAnErrorRaisedWhileApplyingAConfiguration() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.CONFIG_CHANGES_EVERY_POLL).dispatcher);
            val refreshes = new AtomicInteger();
            val evaluator = evaluator(s, (event, details) -> {
                if (refreshes.incrementAndGet() == 1) {
                    throw new IllegalStateException("an event consumer that fails on the first change");
                }
            });

            evaluator.initialize(new ImmutableContext());
            Thread.sleep(POLLING_INTERVAL_MS * 8);
            evaluator.shutdown();

            assertTrue(
                    refreshes.get() > 1,
                    "polling stopped after the first failed refresh, it reached " + refreshes.get() + " refresh(es)");
        }
    }

    @SneakyThrows
    @DisplayName("polling should survive a configuration served without ETag and Last-Modified")
    @Test
    void pollingShouldSurviveAConfigurationServedWithoutEtagAndLastModified() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    // neither cache validator: nothing to compare this response against
                    return new MockResponse()
                            .setResponseCode(200)
                            .setBody("{\"flags\": {\"TEST\": {\"variations\": {\"on\": true},"
                                    + " \"defaultRule\": {\"variation\": \"on\"}}}}");
                }
            });
            val changeEvents = new AtomicInteger();
            val evaluator = evaluator(s, (event, details) -> changeEvents.incrementAndGet());

            evaluator.initialize(new ImmutableContext());
            Thread.sleep(POLLING_INTERVAL_MS * 4);
            val afterPolling = s.getRequestCount();
            Thread.sleep(POLLING_INTERVAL_MS * 4);
            val later = s.getRequestCount();
            val evaluated = evaluator.getBooleanEvaluation("TEST", false, new ImmutableContext("user-key"));
            evaluator.shutdown();

            assertTrue(later > afterPolling, "polling stopped, it stayed at " + afterPolling + " request(s)");
            assertEquals(true, evaluated.getValue());
            // the configuration never changes, so no validator to compare must not mean "changed"
            assertEquals(0, changeEvents.get(), "an unchanged configuration was announced as a change");
        }
    }

    @SneakyThrows
    @DisplayName("a flag carrying unknown fields should still evaluate through the engine")
    @Test
    void aFlagCarryingUnknownFieldsShouldStillEvaluateThroughTheEngine() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.UNKNOWN_RESPONSE_FIELD).dispatcher);
            val evaluator = evaluator(s);
            evaluator.initialize(new ImmutableContext());

            val got = evaluator.getBooleanEvaluation("TEST", false, new ImmutableContext("user-key"));
            evaluator.shutdown();

            // the engine receives the flag with the unrecognised fields still on it
            assertEquals(true, got.getValue());
            assertNull(got.getErrorCode());
        }
    }

    @SneakyThrows
    @DisplayName("should go stale after three consecutive failed refreshes")
    @Test
    void shouldGoStaleAfterThreeConsecutiveFailedRefreshes() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.FAIL_REFRESH_AFTER_INIT).dispatcher);
            val stale = new ArrayList<ProviderEvent>();
            val evaluator = evaluator(s, (event, details) -> {
                if (event == ProviderEvent.PROVIDER_STALE) {
                    stale.add(event);
                }
            });
            evaluator.initialize(new ImmutableContext());

            // two failures are not enough, the third is
            Thread.sleep(POLLING_INTERVAL_MS * 2 + POLLING_INTERVAL_MS / 2);
            assertTrue(stale.isEmpty(), "went stale after fewer than three failures");

            Thread.sleep(POLLING_INTERVAL_MS * 3);
            val evaluated = evaluator.getBooleanEvaluation("bool_targeting_match", false, new ImmutableContext("d45"));
            evaluator.shutdown();

            assertEquals(1, stale.size(), "stale should be announced once, not on every later failure");
            // the configuration it could no longer refresh is still the one it serves: the flag
            // resolves from it rather than reporting the provider as unready
            assertNull(evaluated.getErrorCode());
        }
    }

    @SneakyThrows
    @DisplayName("a successful refresh should restart the failure run")
    @Test
    void aSuccessfulRefreshShouldRestartTheFailureRun() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.FAIL_TWICE_THEN_RECOVER).dispatcher);
            val recorded = new RecordedEvents();
            val evaluator = evaluator(s, recorded);
            evaluator.initialize(new ImmutableContext());

            // two failures, then a success: the two must not carry over into the next run, and
            // recovering from something never announced is not news
            Thread.sleep(POLLING_INTERVAL_MS * 4 + POLLING_INTERVAL_MS / 2);
            assertEquals(List.of(), recorded.events, "an event was announced before any staleness");

            Thread.sleep(POLLING_INTERVAL_MS * 3);
            evaluator.shutdown();
            assertEquals(
                    List.of(ProviderEvent.PROVIDER_STALE),
                    recorded.events,
                    "a fresh run of three failures should go stale, once");
        }
    }

    @SneakyThrows
    @DisplayName("a not modified response should count as a successful refresh")
    @Test
    void aNotModifiedResponseShouldCountAsASuccessfulRefresh() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.NOT_MODIFIED_DURING_FAILURES).dispatcher);
            val stale = new ArrayList<ProviderEvent>();
            val evaluator = evaluator(s, (event, details) -> {
                if (event == ProviderEvent.PROVIDER_STALE) {
                    stale.add(event);
                }
            });
            evaluator.initialize(new ImmutableContext());

            // a 304 says the configuration in hand is current, so it is a refresh that worked
            Thread.sleep(POLLING_INTERVAL_MS * 8);
            evaluator.shutdown();

            assertTrue(stale.isEmpty(), "a 304 was counted as a failed refresh");
        }
    }

    @SneakyThrows
    @DisplayName("should go stale again after recovering from a first stale configuration")
    @Test
    void shouldGoStaleAgainAfterRecoveringFromAFirstStaleConfiguration() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.STALE_AFTER_A_RECOVERY).dispatcher);
            val recorded = new RecordedEvents();
            val evaluator = evaluator(s, recorded);
            evaluator.initialize(new ImmutableContext());

            Thread.sleep(POLLING_INTERVAL_MS * 10);
            evaluator.shutdown();

            // announcing the recovery has to re-arm the run, not spend the provider's one chance
            assertEquals(
                    List.of(ProviderEvent.PROVIDER_STALE, ProviderEvent.PROVIDER_READY, ProviderEvent.PROVIDER_STALE),
                    recorded.events,
                    "a second outage after a recovery was not announced");
        }
    }

    @SneakyThrows
    @DisplayName("should return to ready once refreshes recover after a stale configuration")
    @Test
    void shouldReturnToReadyOnceRefreshesRecoverAfterStale() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.FAIL_UNTIL_RECOVERY).dispatcher);
            val recorded = new RecordedEvents();
            val evaluator = evaluator(s, recorded);
            evaluator.initialize(new ImmutableContext());

            // three failures announce the stale configuration, then every later poll succeeds
            Thread.sleep(POLLING_INTERVAL_MS * 8);
            val evaluated = evaluator.getBooleanEvaluation("bool_targeting_match", false, new ImmutableContext("d45"));
            evaluator.shutdown();

            // the recovery serves the configuration already held, so nothing else is announced:
            // the return to ready happens once, not on every successful poll after it
            assertEquals(
                    List.of(ProviderEvent.PROVIDER_STALE, ProviderEvent.PROVIDER_READY),
                    recorded.events,
                    "the provider did not come back from stale, or came back more than once");
            assertNull(evaluated.getErrorCode());
        }
    }

    @SneakyThrows
    @DisplayName("a not modified response should bring the provider back to ready")
    @Test
    void aNotModifiedResponseShouldBringTheProviderBackToReady() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.NOT_MODIFIED_AFTER_STALE).dispatcher);
            val recorded = new RecordedEvents();
            val evaluator = evaluator(s, recorded);
            evaluator.initialize(new ImmutableContext());

            // a 304 carries no configuration, so it never reaches the refresh consumer: the
            // recovery has to be recognised where the call succeeds, not where a response is applied
            Thread.sleep(POLLING_INTERVAL_MS * 8);
            evaluator.shutdown();

            assertEquals(
                    List.of(ProviderEvent.PROVIDER_STALE, ProviderEvent.PROVIDER_READY),
                    recorded.events,
                    "a 304 did not end the stale condition");
        }
    }

    @SneakyThrows
    @DisplayName("polling should survive a failure raised while announcing the return to ready")
    @Test
    void pollingShouldSurviveAFailureRaisedWhileAnnouncingTheReturnToReady() {
        try (val s = new MockWebServer()) {
            val mock = new GoffApiMock(GoffApiMock.MockMode.FAIL_UNTIL_RECOVERY);
            s.setDispatcher(mock.dispatcher);
            val evaluator = evaluator(s, (event, details) -> {
                throw new IllegalStateException("an emitter that fails, as it does after a shutdown");
            });
            evaluator.initialize(new ImmutableContext());

            // the return to ready is announced from the refresh call itself, where a throw would
            // otherwise be read as one more failed refresh
            Thread.sleep(POLLING_INTERVAL_MS * 6);
            val afterRecovery = mock.getConfigurationCallCount();
            Thread.sleep(POLLING_INTERVAL_MS * 4);
            val later = mock.getConfigurationCallCount();
            val evaluated = evaluator.getBooleanEvaluation("bool_targeting_match", false, new ImmutableContext("d45"));
            evaluator.shutdown();

            assertTrue(later > afterRecovery, "polling stopped, it stayed at " + afterRecovery + " call(s)");
            assertNull(evaluated.getErrorCode());
        }
    }

    @SneakyThrows
    @DisplayName("a failed local evaluation should be answered by the relay proxy")
    @Test
    void aFailedLocalEvaluationShouldBeAnsweredByTheRelayProxy() {
        val evaluator = evaluator(this.server);
        evaluator.initialize(new ImmutableContext());

        val evaluated = evaluator.getStringEvaluation("string_key", "caller-default", contextTooDeepForTheEngine());
        evaluator.shutdown();

        // the value, variant and reason are the relay proxy's, not the ones the local engine failed to
        // produce and not the caller's default
        assertEquals("answered by the relay proxy", evaluated.getValue());
        assertEquals("remoteVariant", evaluated.getVariant());
        assertEquals(Reason.TARGETING_MATCH.name(), evaluated.getReason());
        assertNull(evaluated.getErrorCode());
    }

    @SneakyThrows
    @DisplayName("an unknown flag should not be sent to the relay proxy")
    @Test
    void anUnknownFlagShouldNotBeSentToTheRelayProxy() {
        val evaluator = evaluator(this.server);
        evaluator.initialize(new ImmutableContext());

        // FLAG_NOT_FOUND is this provider's own answer about the caller's key, not an engine failure:
        // the relay proxy holds the same configuration and would answer the same
        assertThrows(
                FlagNotFoundError.class,
                () -> evaluator.getBooleanEvaluation("DOES_NOT_EXIST", false, new ImmutableContext("user-key")));
        evaluator.shutdown();

        assertEquals(
                List.of(),
                goffApiMock.getEvaluatedFlagKeys(),
                "the relay proxy was asked about a flag it does not have either");
    }

    @SneakyThrows
    @DisplayName("a misconfigured flag should not be sent to the relay proxy")
    @Test
    void aMisconfiguredFlagShouldNotBeSentToTheRelayProxy() {
        try (val s = new MockWebServer()) {
            val mock = new GoffApiMock(GoffApiMock.MockMode.MISCONFIGURED_FLAGS);
            s.setDispatcher(mock.dispatcher);
            val evaluator = evaluator(s);
            evaluator.initialize(new ImmutableContext());

            // the engine answers the raw code FLAG_CONFIG, which the SDK enumeration has no member
            // for and which mapping folds into GENERAL. Read after mapping, the trigger would send
            // this to the relay proxy, which holds the same configuration and would refuse it too.
            val error = assertThrows(
                    GeneralError.class,
                    () -> evaluator.getBooleanEvaluation(
                            "flag-without-default-rule", false, new ImmutableContext("user-key")));
            evaluator.shutdown();

            assertEquals(ErrorCode.GENERAL, error.getErrorCode());
            assertEquals(List.of(), mock.getEvaluatedFlagKeys(), "a misconfiguration was sent to the relay proxy");
        }
    }

    @SneakyThrows
    @DisplayName("every failed evaluation should be sent to the relay proxy, not only the first")
    @Test
    void everyFailedEvaluationShouldBeSentToTheRelayProxy() {
        try (val s = new MockWebServer()) {
            val mock = new GoffApiMock(GoffApiMock.MockMode.MISCONFIGURED_FLAGS);
            s.setDispatcher(mock.dispatcher);
            val evaluator = evaluator(s);
            evaluator.initialize(new ImmutableContext());

            // a targeting query the engine cannot parse makes it trap, which answers the raw code
            // GENERAL, so the pair with the misconfigured flag can only pass if the trigger reads it raw
            val evaluations = new ArrayList<Boolean>();
            for (int i = 0; i < 5; i++) {
                evaluations.add(evaluator
                        .getBooleanEvaluation("flag-with-a-broken-query", false, new ImmutableContext("user-" + i))
                        .getValue());
            }
            evaluator.shutdown();
            assertEquals(
                    List.of(true, true, true, true, true), evaluations, "a later caller got a worse answer than one");
            assertEquals(
                    Collections.nCopies(5, "flag-with-a-broken-query"),
                    mock.getEvaluatedFlagKeys(),
                    "the relay proxy stopped being asked");
        }
    }

    @SneakyThrows
    @DisplayName("every fallback should be logged at warning level")
    @Test
    void everyFallbackShouldBeLoggedAtWarningLevel() {
        try (val s = new MockWebServer()) {
            val mock = new GoffApiMock(GoffApiMock.MockMode.MISCONFIGURED_FLAGS);
            s.setDispatcher(mock.dispatcher);
            val evaluator = evaluator(s);
            evaluator.initialize(new ImmutableContext());

            val warnings = warningsWhile(() -> {
                evaluator.getBooleanEvaluation("flag-with-a-broken-query", false, new ImmutableContext("user-1"));
                evaluator.getBooleanEvaluation("flag-with-a-broken-query", false, new ImmutableContext("user-2"));
            });
            evaluator.shutdown();

            assertEquals(2, warnings.size(), "a fallback went unlogged: " + warnings);
            assertTrue(
                    warnings.get(0).contains("flag-with-a-broken-query"),
                    "the warning does not name the flag: " + warnings.get(0));
        }
    }

    @SneakyThrows
    @DisplayName("a fallback result should say it was evaluated remotely")
    @Test
    void aFallbackResultShouldSayItWasEvaluatedRemotely() {
        try (val s = new MockWebServer()) {
            val mock = new GoffApiMock(GoffApiMock.MockMode.MISCONFIGURED_FLAGS);
            s.setDispatcher(mock.dispatcher);
            val evaluator = evaluator(s);
            evaluator.initialize(new ImmutableContext());

            // the engine trips on the flag's broken query; the relay proxy answers it successfully
            val evaluated =
                    evaluator.getBooleanEvaluation("flag-with-a-broken-query", false, new ImmutableContext("user-key"));
            evaluator.shutdown();

            assertEquals(true, evaluated.getFlagMetadata().getBoolean(Const.METADATA_EVALUATED_REMOTELY));
            // the marker is added to the relay proxy's metadata, not put in place of it
            assertEquals(true, evaluated.getFlagMetadata().getBoolean("gofeatureflag_cacheable"));
            assertEquals(
                    "a flag only the relay proxy can evaluate",
                    evaluated.getFlagMetadata().getString("description"));
        }
    }

    @SneakyThrows
    @DisplayName("an evaluation the engine answers should not go through the fallback")
    @Test
    void anEvaluationTheEngineAnswersShouldNotGoThroughTheFallback() {
        val evaluator = evaluator(this.server);
        evaluator.initialize(new ImmutableContext());

        val evaluated = new ArrayList<ProviderEvaluation<Boolean>>();
        val warnings = warningsWhile(() -> evaluated.add(
                evaluator.getBooleanEvaluation("bool_targeting_match", false, new ImmutableContext("d45"))));
        evaluator.shutdown();

        assertNull(evaluated.get(0).getErrorCode());
        assertEquals(List.of(), goffApiMock.getEvaluatedFlagKeys());
        assertNull(evaluated.get(0).getFlagMetadata().getBoolean(Const.METADATA_EVALUATED_REMOTELY));
        assertEquals(List.of(), warnings);
    }

    @SneakyThrows
    @DisplayName("a relay proxy that fails too should leave the engine's error standing")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"flag-the-proxy-does-not-have", "flag-the-proxy-answers-badly"})
    void aRelayProxyThatFailsTooShouldLeaveTheEnginesErrorStanding(String flagKey) {
        try (val s = new MockWebServer()) {
            val mock = new GoffApiMock(GoffApiMock.MockMode.MISCONFIGURED_FLAGS);
            s.setDispatcher(mock.dispatcher);
            val evaluator = evaluator(s);
            evaluator.initialize(new ImmutableContext());

            val error = assertThrows(
                    GeneralError.class,
                    () -> evaluator.getBooleanEvaluation(flagKey, false, new ImmutableContext("user-key")));
            evaluator.shutdown();

            assertEquals("Trapped on unreachable instruction", error.getMessage());
            assertEquals(List.of(flagKey), mock.getEvaluatedFlagKeys());
        }
    }

    @SneakyThrows
    @DisplayName("should report PROVIDER_NOT_READY before any configuration is loaded")
    @Test
    void shouldReportProviderNotReadyBeforeAnyConfigurationIsLoaded() {
        val evaluator = evaluator(this.server);

        val error = assertThrows(
                ProviderNotReadyError.class,
                () -> evaluator.getBooleanEvaluation("bool_targeting_match", false, new ImmutableContext("user-key")));

        assertEquals(ErrorCode.PROVIDER_NOT_READY, error.getErrorCode());
        assertEquals(
                "impossible to evaluate flag bool_targeting_match: no flag configuration has been loaded yet",
                error.getMessage());
    }

    @SneakyThrows
    @DisplayName("should report PROVIDER_NOT_READY, not FLAG_NOT_FOUND, after a failed init")
    @Test
    void shouldReportProviderNotReadyAfterAFailedInit() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.ENDPOINT_ERROR_404).dispatcher);
            val evaluator = evaluator(s);
            assertThrows(FlagConfigurationEndpointNotFound.class, () -> evaluator.initialize(new ImmutableContext()));

            val error = assertThrows(
                    ProviderNotReadyError.class,
                    () -> evaluator.getBooleanEvaluation(
                            "bool_targeting_match", false, new ImmutableContext("user-key")));

            assertEquals(ErrorCode.PROVIDER_NOT_READY, error.getErrorCode());
        }
    }

    @SneakyThrows
    @DisplayName("an empty but valid configuration counts as loaded")
    @Test
    void anEmptyButValidConfigurationCountsAsLoaded() {
        try (val s = new MockWebServer()) {
            s.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.EMPTY_FLAG_CONFIG).dispatcher);
            val evaluator = evaluator(s);
            evaluator.initialize(new ImmutableContext());

            // a relay proxy legitimately serving zero flags is loaded: the key really is unknown
            assertThrows(
                    FlagNotFoundError.class,
                    () -> evaluator.getBooleanEvaluation(
                            "bool_targeting_match", false, new ImmutableContext("user-key")));
            evaluator.shutdown();
        }
    }

    @SneakyThrows
    @DisplayName("should stay loaded across shutdown and re-init")
    @Test
    void shouldStayLoadedAcrossShutdownAndReInit() {
        val evaluator = evaluator(this.server);
        evaluator.initialize(new ImmutableContext());
        evaluator.shutdown();
        evaluator.initialize(new ImmutableContext());

        // the stored ETag survives shutdown(), so a re-init answered 304 must keep serving what it holds
        assertThrows(
                FlagNotFoundError.class,
                () -> evaluator.getBooleanEvaluation("DOES_NOT_EXIST", false, new ImmutableContext("user-key")));
        evaluator.shutdown();
    }

    @SneakyThrows
    @DisplayName("a missing targeting key should be passed through to the engine")
    @Test
    void aMissingTargetingKeyShouldBePassedThroughToTheEngine() {
        val evaluator = evaluator(this.server);
        evaluator.initialize(new ImmutableContext());

        // object_key resolves through a default rule that does not bucket, so it needs no targeting key
        val got = evaluator.getObjectEvaluation("object_key", null, new ImmutableContext());

        assertNull(got.getErrorCode());
        assertEquals("varA", got.getVariant());
        evaluator.shutdown();
    }

    @SneakyThrows
    @DisplayName("the engine should report TARGETING_KEY_MISSING only for a flag that buckets")
    @Test
    void theEngineShouldReportTargetingKeyMissingOnlyForAFlagThatBuckets() {
        val evaluator = evaluator(this.server);
        evaluator.initialize(new ImmutableContext());

        val error = assertThrows(
                TargetingKeyMissingError.class,
                () -> evaluator.getStringEvaluation("string_key", "default", new ImmutableContext()));

        assertEquals(ErrorCode.TARGETING_KEY_MISSING, error.getErrorCode());
        evaluator.shutdown();
    }

    @Nested
    @DisplayName("conversion of an engine response into a resolution")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Conversion {
        @DisplayName("Should raise the SDK error matching the engine's error code")
        @ParameterizedTest(name = "{0}")
        @MethodSource("engineErrorCodes")
        void shouldRaiseTheSdkErrorMatchingTheEnginesErrorCode(
                String engineCode, Class<? extends OpenFeatureError> expectedError, ErrorCode expectedCode) {
            val response = new GoFeatureFlagResponse();
            response.setErrorCode(engineCode);
            response.setErrorDetails("details for " + engineCode);

            val error = assertThrows(
                    expectedError, () -> toProviderEvaluation("test-flag", false, response, Boolean.class));

            assertEquals(expectedCode, error.getErrorCode());
            assertEquals("details for " + engineCode, error.getMessage());
        }

        @DisplayName("Should handle successful evaluation")
        @Test
        void shouldHandleSuccessfulEvaluation() {
            ProviderEvaluation<Boolean> result =
                    toProviderEvaluation("test-flag", false, responseWithValue(true), Boolean.class);

            assertEquals(true, result.getValue());
            assertEquals(Reason.TARGETING_MATCH.name(), result.getReason());
            assertEquals("enabled", result.getVariant());
            assertNull(result.getErrorCode());
        }

        @DisplayName("Should accept an int number for the double resolver")
        @Test
        void shouldAcceptAnIntegralNumberForTheDoubleResolver() {
            assertEquals(100.0, doubleEvaluationOf(100));
        }

        @DisplayName("Should accept an int number larger than an int for the double resolver")
        @Test
        void shouldAcceptAnIntegralNumberLargerThanAnIntForTheDoubleResolver() {
            // above Integer.MAX_VALUE Jackson decodes a JSON integer to Long
            assertEquals(3000000000.0, doubleEvaluationOf(3000000000L));
        }

        @DisplayName("Should accept a decimal number for the double resolver")
        @Test
        void shouldAcceptADecimalNumberForTheDoubleResolver() {
            assertEquals(101.25, doubleEvaluationOf(101.25));
        }

        @DisplayName("Should not let a boolean satisfy the double resolver")
        @Test
        void shouldNotLetABooleanSatisfyTheDoubleResolver() {
            assertThrows(
                    TypeMismatchError.class,
                    () -> toProviderEvaluation("test-flag", 0.0, responseWithValue(true), Double.class));
        }

        @DisplayName("Should not let a decimal number satisfy the integer resolver")
        @Test
        void shouldNotLetADecimalNumberSatisfyTheIntegerResolver() {
            assertThrows(
                    TypeMismatchError.class,
                    () -> toProviderEvaluation("test-flag", 0, responseWithValue(101.25), Integer.class));
        }

        @DisplayName("Should return the caller default and keep the engine details when the value is null")
        @Test
        void shouldReturnTheCallerDefaultAndKeepTheEngineDetailsWhenTheValueIsNull() {
            val response = responseWithValue(null);
            response.setMetadata(Map.of("description", "a flag with no value"));

            ProviderEvaluation<Boolean> result = toProviderEvaluation("test-flag", true, response, Boolean.class);

            assertEquals(true, result.getValue());
            assertEquals(Reason.DEFAULT.name(), result.getReason());
            assertNull(result.getVariant());
            assertEquals("a flag with no value", result.getFlagMetadata().getString("description"));
            assertNull(result.getErrorCode());
        }

        @DisplayName("Should keep the flag metadata when the flag is disabled")
        @Test
        void shouldKeepTheFlagMetadataWhenTheFlagIsDisabled() {
            val response = responseWithValue(true);
            response.setReason(Reason.DISABLED.name());
            response.setVariationType("SdkDefault");
            response.setMetadata(Map.of("description", "a disabled flag", "gofeatureflag_cacheable", true));

            ProviderEvaluation<Boolean> result = toProviderEvaluation("test-flag", false, response, Boolean.class);

            assertEquals(false, result.getValue());
            assertEquals(Reason.DISABLED.name(), result.getReason());
            assertEquals("a disabled flag", result.getFlagMetadata().getString("description"));
            assertEquals(true, result.getFlagMetadata().getBoolean("gofeatureflag_cacheable"));
        }

        @DisplayName("Should not return a zero value when the value is null")
        @Test
        void shouldNotReturnAZeroValueWhenTheValueIsNull() {
            assertEquals(
                    42,
                    toProviderEvaluation("test-flag", 42, responseWithValue(null), Integer.class)
                            .getValue());
            assertEquals(
                    "caller-default",
                    toProviderEvaluation("test-flag", "caller-default", responseWithValue(null), String.class)
                            .getValue());
            assertEquals(
                    new Value("caller-default"),
                    toProviderEvaluation("test-flag", new Value("caller-default"), responseWithValue(null), Value.class)
                            .getValue());
        }

        private GoFeatureFlagResponse responseWithValue(Object value) {
            val response = new GoFeatureFlagResponse();
            response.setValue(value);
            response.setReason(Reason.TARGETING_MATCH.name());
            response.setVariationType("enabled");
            return response;
        }

        private Double doubleEvaluationOf(Object engineValue) {
            return toProviderEvaluation("test-flag", 0.0, responseWithValue(engineValue), Double.class)
                    .getValue();
        }

        private Stream<Arguments> engineErrorCodes() {
            return Stream.of(
                    Arguments.of("FLAG_NOT_FOUND", FlagNotFoundError.class, ErrorCode.FLAG_NOT_FOUND),
                    Arguments.of("GENERAL", GeneralError.class, ErrorCode.GENERAL),
                    Arguments.of(
                            "TARGETING_KEY_MISSING", TargetingKeyMissingError.class, ErrorCode.TARGETING_KEY_MISSING),
                    Arguments.of("INVALID_CONTEXT", InvalidContextError.class, ErrorCode.INVALID_CONTEXT),
                    Arguments.of("PARSE_ERROR", ParseError.class, ErrorCode.PARSE_ERROR),
                    // specific to GO Feature Flag, no SDK equivalent
                    Arguments.of("FLAG_CONFIG", GeneralError.class, ErrorCode.GENERAL));
        }
    }

    @SneakyThrows
    private InProcessEvaluator evaluator(MockWebServer srv, BiConsumer<ProviderEvent, ProviderEventDetails> emitter) {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(srv.url("").toString())
                .flagChangePollingIntervalMs(POLLING_INTERVAL_MS)
                .build();
        return new InProcessEvaluator(
                GoFeatureFlagApi.builder().options(options).build(), options, emitter);
    }

    private InProcessEvaluator evaluator(MockWebServer srv) {
        return evaluator(srv, (event, details) -> {});
    }

    /**
     * A context the evaluation engine's own guards refuse to read, so that the engine answers
     * PARSE_ERROR rather than a value. It is a real engine failure, not a simulated one.
     */
    @SneakyThrows
    private static ImmutableContext contextTooDeepForTheEngine() {
        val deep = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            deep.append("{\"a\":");
        }
        deep.append("1");
        for (int i = 0; i < 400; i++) {
            deep.append("}");
        }
        val asMap = Const.DESERIALIZE_OBJECT_MAPPER.readValue(
                "{\"targetingKey\":\"user-key\",\"deep\":" + deep + "}", Map.class);
        return new ImmutableContext(Structure.mapToStructure(asMap).asMap());
    }

    /** Runs an evaluation and returns the warnings InProcessEvaluator logged while it ran. */
    private static List<String> warningsWhile(final Runnable evaluation) {
        val logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(InProcessEvaluator.class);
        val captured = new ArrayList<String>();
        val appender = new AbstractAppender("fallback-capture", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (Level.WARN.equals(event.getLevel())) {
                    captured.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            evaluation.run();
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
        return captured;
    }

    /** Records the events the evaluator emits, in the order it emits them. */
    private static final class RecordedEvents implements BiConsumer<ProviderEvent, ProviderEventDetails> {
        final List<ProviderEvent> events = new ArrayList<>();

        @Override
        public void accept(ProviderEvent event, ProviderEventDetails eventDetails) {
            this.events.add(event);
        }
    }
}
