package dev.openfeature.contrib.providers.gofeatureflag;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidExporterMetadata;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.util.GoffApiMock;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.MutableTrackingEventDetails;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@Slf4j
class GoFeatureFlagProviderTest {
    private MockWebServer server;
    private GoffApiMock goffAPIMock;
    private HttpUrl baseUrl;
    private String testName;

    @BeforeEach
    void beforeEach(TestInfo testInfo) throws IOException {
        this.server = new MockWebServer();
        goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.DEFAULT);
        this.server.setDispatcher(goffAPIMock.dispatcher);
        this.server.start();
        baseUrl = server.url("");
        this.testName = testInfo.getDisplayName();
    }

    @SneakyThrows
    @AfterEach
    void afterEach() throws IOException {
        OpenFeatureAPI.getInstance().shutdown();

        Thread.sleep(50L);
        this.server.close();
        this.server = null;
        baseUrl = null;
    }

    @Nested
    @DisplayName("Common tests working with all evaluation types")
    class Common {
        @SneakyThrows
        @Test
        void getMetadata_validate_name() {
            assertEquals(
                    "GO Feature Flag Provider",
                    new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                            .endpoint("https://gofeatureflag.org")
                            .timeout(1000)
                            .build())
                            .getMetadata()
                            .getName());
        }

        @Test
        void constructor_options_null() {
            assertThrows(InvalidOptions.class, () -> new GoFeatureFlagProvider(null));
        }

        @Test
        void constructor_options_empty() {
            assertThrows(
                    InvalidOptions.class,
                    () -> new GoFeatureFlagProvider(
                            GoFeatureFlagProviderOptions.builder().build()));
        }

        @SneakyThrows
        @Test
        void constructor_options_empty_endpoint() {
            assertThrows(
                    InvalidEndpoint.class,
                    () -> new GoFeatureFlagProvider(
                            GoFeatureFlagProviderOptions.builder().endpoint("").build()));
        }

        @SneakyThrows
        @Test
        void constructor_options_only_timeout() {
            assertThrows(
                    InvalidEndpoint.class,
                    () -> new GoFeatureFlagProvider(
                            GoFeatureFlagProviderOptions.builder().timeout(10000).build()));
        }

        @SneakyThrows
        @Test
        void constructor_options_valid_endpoint() {
            assertDoesNotThrow(() -> new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .endpoint("http://localhost:1031")
                    .build()));
        }

        @DisplayName("Should error if the metadata is not a valid type")
        @SneakyThrows
        @Test
        void shouldErrorIfTheMetadataIsNotAValidType() {
            assertThrows(
                    InvalidExporterMetadata.class,
                    () -> new GoFeatureFlagProvider(
                            GoFeatureFlagProviderOptions.builder()
                                    .endpoint(baseUrl.toString())
                                    .exporterMetadata(Map.of(
                                            // object is not a valid metadata
                                            "invalid-metadata", goffAPIMock
                                    ))
                                    .evaluationType(EvaluationType.REMOTE)
                                    .build()
                    )
            );
        }

        @DisplayName("Should error if invalid flush interval is set")
        @SneakyThrows
        @Test
        void shouldErrorIfInvalidFlushIntervalIsSet() {
            assertThrows(
                    InvalidOptions.class,
                    () -> new GoFeatureFlagProvider(
                            GoFeatureFlagProviderOptions.builder()
                                    .flushIntervalMs(-1L)
                                    .maxPendingEvents(1000)
                                    .endpoint(baseUrl.toString())
                                    .evaluationType(EvaluationType.IN_PROCESS)
                                    .build()
                    )
            );
        }

        @DisplayName("Should error if invalid max pending events is set")
        @SneakyThrows
        @Test
        void shouldErrorIfInvalidMaxPendingEventsIsSet() {
            assertThrows(
                    InvalidOptions.class,
                    () -> new GoFeatureFlagProvider(
                            GoFeatureFlagProviderOptions.builder()
                                    .flushIntervalMs(100L)
                                    .maxPendingEvents(-1000)
                                    .endpoint(baseUrl.toString())
                                    .evaluationType(EvaluationType.IN_PROCESS)
                                    .build()
                    )
            );
        }
    }

    @Nested
    class InProcessEvaluation {
        @DisplayName("Should use in process evaluation by default")
        @SneakyThrows
        @Test
        void shouldUseInProcessByDefault() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder().endpoint(baseUrl.toString()).build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            client.getBooleanDetails("bool_targeting_match", false, new MutableContext());
            val want = "/v1/flag/configuration";
            assertEquals(want, server.takeRequest().getPath());

        }

        @DisplayName("Should use in process evaluation if option is set")
        @SneakyThrows
        @Test
        void shouldUseInProcessIfOptionIsSet() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            client.getBooleanDetails("bool_targeting_match", false, new MutableContext());
            val want = "/v1/flag/configuration";
            assertEquals(want, server.takeRequest().getPath());
        }

        @DisplayName("should throw an error if the endpoint is not available")
        @SneakyThrows
        @Test
        void shouldThrowAnErrorIfEndpointNotAvailable() {
            try (val s = new MockWebServer()) {
                val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.ENDPOINT_ERROR);
                s.setDispatcher(goffAPIMock.dispatcher);
                GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                        .endpoint(s.url("").toString())
                        .timeout(1000)
                        .build());
                assertThrows(GeneralError.class, () ->
                        OpenFeatureAPI.getInstance().setProviderAndWait(testName, g));
            }
        }

        @DisplayName("should throw an error if api key is missing")
        @SneakyThrows
        @Test
        void shouldThrowAnErrorIfApiKeyIsMissing() {
            try (val s = new MockWebServer()) {
                val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.ENDPOINT_ERROR);
                s.setDispatcher(goffAPIMock.dispatcher);
                GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                        .endpoint(s.url("").toString())
                        .timeout(1000)
                        .build());
                assertThrows(GeneralError.class, () ->
                        OpenFeatureAPI.getInstance().setProviderAndWait(testName, g));
            }
        }

        @DisplayName("should return FLAG_NOT_FOUND if the flag does not exists")
        @SneakyThrows
        @Test
        void shouldReturnFlagNotFoundIfFlagDoesNotExists() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getBooleanDetails("DOES_NOT_EXISTS", false, TestUtils.defaultEvaluationContext);

            val want = FlagEvaluationDetails.<Boolean>builder()
                    .value(false)
                    .flagKey("DOES_NOT_EXISTS")
                    .reason(Reason.ERROR.name())
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .errorMessage("Flag DOES_NOT_EXISTS was not found in your configuration")
                    .build();
            assertEquals(want, got);
        }

        @DisplayName("Should throw an error if we expect a boolean and got another type")
        @SneakyThrows
        @Test
        void shouldThrowAnErrorIfWeExpectABooleanAndGotAnotherType() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getBooleanDetails("string_key", false, TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<Boolean>builder()
                    .flagKey("string_key")
                    .value(false)
                    .reason(Reason.ERROR.name())
                    .errorCode(ErrorCode.TYPE_MISMATCH)
                    .errorMessage(
                            "Flag value string_key had unexpected type class java.lang.String, expected class java.lang.Boolean.")
                    .build();
            assertEquals(want, got);
        }

        @DisplayName("Should resolve a valid boolean flag with TARGETING MATCH reason")
        @SneakyThrows
        @Test
        void shouldResolveAValidBooleanFlagWithTargetingMatchReason() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getBooleanDetails("bool_targeting_match", false, TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<Boolean>builder()
                    .value(true)
                    .variant("enabled")
                    .flagKey("bool_targeting_match")
                    .reason(Reason.TARGETING_MATCH.name())
                    .flagMetadata(ImmutableMetadata.builder()
                            .addString("description", "this is a test flag")
                            .addBoolean("defaultValue", false)
                            .build())
                    .build();
            assertEquals(want, got);
        }

        @DisplayName("Should resolve a valid string flag with TARGETING MATCH reason")
        @SneakyThrows
        @Test
        void shouldResolveAValidStringFlagWithTargetingMatchReason() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getStringDetails("string_key", "", TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<String>builder()
                    .value("CC0002")
                    .variant("color1")
                    .flagKey("string_key")
                    .reason(Reason.STATIC.name())
                    .flagMetadata(ImmutableMetadata.builder()
                            .addString("description", "this is a test flag")
                            .addString("defaultValue", "CC0000")
                            .build())
                    .build();
            assertEquals(want, got);
        }

        @DisplayName("Should resolve a valid double flag with TARGETING MATCH reason")
        @SneakyThrows
        @Test
        void shouldResolveAValidDoubleFlagWithTargetingMatchReason() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getDoubleDetails("double_key", 100.10, TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<Double>builder()
                    .value(101.25)
                    .variant("medium")
                    .flagKey("double_key")
                    .reason(Reason.TARGETING_MATCH.name())
                    .flagMetadata(ImmutableMetadata.builder()
                            .addString("description", "this is a test flag")
                            .addDouble("defaultValue", 100.25)
                            .build())
                    .build();
            assertEquals(want, got);
        }

        @DisplayName("Should resolve a valid integer flag with TARGETING MATCH reason")
        @SneakyThrows
        @Test
        void shouldResolveAValidIntegerFlagWithTargetingMatchReason() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getIntegerDetails("integer_key", 1000, TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<Integer>builder()
                    .value(101)
                    .variant("medium")
                    .flagKey("integer_key")
                    .reason(Reason.TARGETING_MATCH.name())
                    .flagMetadata(ImmutableMetadata.builder()
                            .addString("description", "this is a test flag")
                            .addInteger("defaultValue", 1000)
                            .build())
                    .build();
            assertEquals(want, got);
        }

        @DisplayName("Should resolve a valid object flag with TARGETING MATCH reason")
        @SneakyThrows
        @Test
        void shouldResolveAValidObjectFlagWithTargetingMatchReason() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getObjectDetails("object_key",
                    Value.objectToValue(new MutableStructure().add("default", "true")),
                    TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<Object>builder()
                    .value(Value.objectToValue(new MutableStructure().add("test", "false")))
                    .variant("varB")
                    .flagKey("object_key")
                    .reason(Reason.TARGETING_MATCH.name())
                    .build();
            assertEquals(want, got);
        }

        @DisplayName("Should use boolean default value if the flag is disabled")
        @SneakyThrows
        @Test
        void shouldUseBooleanDefaultValueIfTheFlagIsDisabled() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getBooleanDetails("disabled_bool", false, TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<Boolean>builder()
                    .value(false)
                    .variant("SdkDefault")
                    .flagKey("disabled_bool")
                    .reason(Reason.DISABLED.name())
                    .build();
            assertEquals(want, got);
        }

        @DisplayName("Should emit configuration change event, if config has changed")
        @SneakyThrows
        @Test
        void shouldEmitConfigurationChangeEventIfConfigHasChanged() {
            val s = new MockWebServer();
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.CHANGE_CONFIG_AFTER_1ST_EVAL);
            s.setDispatcher(goffAPIMock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
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

        @DisplayName("Should not emit configuration change event, if config has not changed")
        @SneakyThrows
        @Test
        void shouldNotEmitConfigurationChangeEventIfConfigHasNotChanged() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .flagChangePollingIntervalMs(100L)
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );

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

        @DisplayName("Should change evaluation details if config has changed")
        @SneakyThrows
        @Test
        void shouldChangeEvaluationValueIfConfigHasChanged() {
            val s = new MockWebServer();
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.CHANGE_CONFIG_AFTER_1ST_EVAL);
            s.setDispatcher(goffAPIMock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .flagChangePollingIntervalMs(100L)
                            .endpoint(s.url("").toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
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
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .flagChangePollingIntervalMs(100L)
                            .endpoint(s.url("").toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            assertThrows(
                    GeneralError.class,
                    () -> OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider)
            );
        }

        @DisplayName("Should ignore configuration if etag is different by last-modified is older")
        @SneakyThrows
        @Test
        void shouldIgnoreConfigurationIfEtagIsDifferentByLastModifiedIsOlder() {
            val s = new MockWebServer();
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.SERVE_OLD_CONFIGURATION);
            s.setDispatcher(goffAPIMock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .flagChangePollingIntervalMs(100L)
                            .endpoint(s.url("").toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
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

    @Nested
    class DataCollectorHook {
        @DisplayName("Should send the evaluation information to the data collector")
        @SneakyThrows
        @Test
        void shouldSendTheEvaluationInformationToTheDataCollector() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .flushIntervalMs(150L)
                            .maxPendingEvents(100)
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            client.getIntegerDetails("integer_key", 1000, TestUtils.defaultEvaluationContext);
            client.getIntegerDetails("integer_key", 1000, TestUtils.defaultEvaluationContext);
            Thread.sleep(400L);
            assertEquals(1, goffAPIMock.getCollectorRequestsHistory().size());
        }

        @DisplayName("Should omit events if max pending events is reached")
        @SneakyThrows
        @Test
        void shouldCallMultipleTimeTheDataCollectorIfMaxPendingEventsIsReached() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .flushIntervalMs(100L)
                            .maxPendingEvents(1)
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            client.getIntegerDetails("integer_key", 1000, TestUtils.defaultEvaluationContext);
            client.getIntegerDetails("integer_key", 1000, TestUtils.defaultEvaluationContext);
            Thread.sleep(180L);
            assertEquals(2, goffAPIMock.getCollectorRequestsHistory().size());
        }

        @DisplayName("Should not send evaluation event if flag has tracking disabled")
        @SneakyThrows
        @Test
        void shouldNotSendEvaluationEventIfFlagHasTrackingDisabled() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .flushIntervalMs(100L)
                            .maxPendingEvents(1)
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            client.getStringDetails("string_key", "default", TestUtils.defaultEvaluationContext);
            client.getStringDetails("string_key", "default", TestUtils.defaultEvaluationContext);
            Thread.sleep(180L);
            assertEquals(0, goffAPIMock.getCollectorRequestsHistory().size());
        }

        @DisplayName("Should not send events for remote evaluation")
        @SneakyThrows
        @Test
        void shouldResolveAValidStringFlag() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .flushIntervalMs(100L)
                            .maxPendingEvents(1)
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.REMOTE)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            client.getStringDetails("string_flag", "false", TestUtils.defaultEvaluationContext);
            Thread.sleep(180L);
            assertEquals(0, goffAPIMock.getCollectorRequestsHistory().size());
        }
    }

    @Nested
    class EnrichEvaluationContext {
        @DisplayName("Should add to the context the exporter metadata to the evaluation context")
        @SneakyThrows
        @Test
        void shouldAddToTheContextTheExporterMetadataToTheEvaluationContext() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .exporterMetadata(Map.of(
                                    "test-string", "testing-provider",
                                    "test-int", 1,
                                    "test-double", 3.14,
                                    "test-boolean", true
                            ))
                            .evaluationType(EvaluationType.REMOTE)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            client.getBooleanDetails("bool_flag", false, TestUtils.defaultEvaluationContext);
            val got = Const.DESERIALIZE_OBJECT_MAPPER.readValue(goffAPIMock.getLastRequestBody(),
                    HashMap.class);

            val context = new HashMap<String, Object>();
            context.put("targetingKey", "d45e303a-38c2-11ed-a261-0242ac120002");
            context.put("rate", 3.14);
            context.put("company_info", Map.of(
                    "size", 120,
                    "name", "my_company"
            ));
            context.put("anonymous", false);
            context.put("email", "john.doe@gofeatureflag.org");
            context.put("lastname", "doe");
            context.put("firstname", "john");
            context.put("age", 30);
            context.put("gofeatureflag", Map.of(
                    "exporterMetadata", Map.of(
                            "test-double", 3.14,
                            "test-int", 1,
                            "test-boolean", true,
                            "test-string", "testing-provider"
                    )));
            context.put("professional", true);
            context.put("labels", List.of("pro", "beta"));

            Map<String, Object> want = new HashMap<>();
            want.put("context", context);
            assertEquals(want, got);
        }

        @DisplayName("Should not add gofeatureflag key in exporterMetadata if the exporterMetadata is empty")
        @SneakyThrows
        @Test
        void shouldNotAddGoffeatureflagKeyInExporterMetadataIfTheExporterMetadataIsEmpty() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.REMOTE)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            client.getBooleanDetails("bool_flag", false, TestUtils.defaultEvaluationContext);
            val got = Const.DESERIALIZE_OBJECT_MAPPER.readValue(goffAPIMock.getLastRequestBody(),
                    HashMap.class);

            val context = new HashMap<String, Object>();
            context.put("targetingKey", "d45e303a-38c2-11ed-a261-0242ac120002");
            context.put("rate", 3.14);
            context.put("company_info", Map.of(
                    "size", 120,
                    "name", "my_company"
            ));
            context.put("anonymous", false);
            context.put("email", "john.doe@gofeatureflag.org");
            context.put("lastname", "doe");
            context.put("firstname", "john");
            context.put("age", 30);
            context.put("professional", true);
            context.put("labels", List.of("pro", "beta"));

            Map<String, Object> want = new HashMap<>();
            want.put("context", context);
            assertEquals(want, got);
        }
    }

    @Nested
    class RemoteEvaluation {
        @DisplayName("should error if the endpoint is not available")
        @SneakyThrows
        @Test
        void shouldErrorIfEndpointNotAvailable() {
            try (val s = new MockWebServer()) {
                val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.ENDPOINT_ERROR);
                s.setDispatcher(goffAPIMock.dispatcher);
                GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                        .endpoint(s.url("").toString())
                        .evaluationType(EvaluationType.REMOTE)
                        .timeout(1000)
                        .build());
                OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
                val client = OpenFeatureAPI.getInstance().getClient(testName);
                val got = client.getBooleanDetails("bool_flag", false, TestUtils.defaultEvaluationContext);
                val want = FlagEvaluationDetails.<Boolean>builder()
                        .value(false)
                        .flagKey("bool_flag")
                        .reason(Reason.ERROR.name())
                        .errorCode(ErrorCode.GENERAL)
                        .errorMessage("Unknown error while retrieving flag ")
                        .build();
                assertEquals(want, got);
            }
        }

        @DisplayName("should error if no API Key provided")
        @SneakyThrows
        @Test
        void shouldErrorIfApiKeyIsMissing() {
            try (val s = new MockWebServer()) {
                val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.API_KEY_MISSING);
                s.setDispatcher(goffAPIMock.dispatcher);
                GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                        .endpoint(s.url("").toString())
                        .evaluationType(EvaluationType.REMOTE)
                        .timeout(1000)
                        .build());
                OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
                val client = OpenFeatureAPI.getInstance().getClient(testName);
                val got = client.getBooleanDetails("bool_flag", false, TestUtils.defaultEvaluationContext);
                val want = FlagEvaluationDetails.<Boolean>builder()
                        .value(false)
                        .flagKey("bool_flag")
                        .reason(Reason.ERROR.name())
                        .errorCode(ErrorCode.GENERAL)
                        .errorMessage("authentication/authorization error")
                        .build();
                assertEquals(want, got);
            }
        }

        @DisplayName("should error if API Key is invalid")
        @SneakyThrows
        @Test
        void shouldErrorIfApiKeyIsInvalid() {
            try (val s = new MockWebServer()) {
                val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.INVALID_API_KEY);
                s.setDispatcher(goffAPIMock.dispatcher);
                GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                        .endpoint(s.url("").toString())
                        .evaluationType(EvaluationType.REMOTE)
                        .apiKey("invalid")
                        .timeout(1000)
                        .build());
                OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
                val client = OpenFeatureAPI.getInstance().getClient(testName);
                val got = client.getBooleanDetails("bool_flag", false, TestUtils.defaultEvaluationContext);
                val want = FlagEvaluationDetails.<Boolean>builder()
                        .value(false)
                        .flagKey("bool_flag")
                        .reason(Reason.ERROR.name())
                        .errorCode(ErrorCode.GENERAL)
                        .errorMessage("authentication/authorization error")
                        .build();
                assertEquals(want, got);
            }
        }

        @DisplayName("should error if the flag is not found")
        @SneakyThrows
        @Test
        void shouldErrorIfFlagNotFound() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.REMOTE)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getBooleanDetails("does-not-exists", false, TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<Boolean>builder()
                    .value(false)
                    .flagKey("does-not-exists")
                    .reason(Reason.ERROR.name())
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .errorMessage("Flag does-not-exists not found")
                    .build();
            assertEquals(want, got);
        }

        @DisplayName("Should error if evaluating the wrong type")
        @SneakyThrows
        @Test
        void shouldErrorIfEvaluatingTheWrongType() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.REMOTE)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getStringDetails("bool_flag", "default", TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<String>builder()
                    .value("default")
                    .flagKey("bool_flag")
                    .reason(Reason.ERROR.name())
                    .errorMessage(
                            "Flag value bool_flag had unexpected type class java.lang.Boolean, expected class java.lang.String.")
                    .errorCode(ErrorCode.TYPE_MISMATCH)
                    .build();
            assertEquals(want, got);
        }

        @DisplayName("Should resolve a valid boolean flag")
        @SneakyThrows
        @Test
        void shouldResolveAValidBooleanFlag() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.REMOTE)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getBooleanDetails("bool_flag", false, TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<Boolean>builder()
                    .value(true)
                    .variant("enabled")
                    .flagKey("bool_flag")
                    .reason(Reason.TARGETING_MATCH.name())
                    .flagMetadata(ImmutableMetadata.builder()
                            .addString("description", "A flag that is always off")
                            .build())
                    .build();
            assertEquals(want, got);
        }

        @DisplayName("Should resolve a valid string flag")
        @SneakyThrows
        @Test
        void shouldResolveAValidStringFlag() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.REMOTE)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getStringDetails("string_flag", "false", TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<String>builder()
                    .value("string value")
                    .variant("variantA")
                    .flagKey("string_flag")
                    .reason(Reason.TARGETING_MATCH.name())
                    .flagMetadata(ImmutableMetadata.builder()
                            .addString("description", "A flag that is always off")
                            .build())
                    .build();
            assertEquals(want, got);
        }

        @DisplayName("Should resolve a valid int flag")
        @SneakyThrows
        @Test
        void shouldResolveAValidIntFlag() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.REMOTE)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getIntegerDetails("int_flag", 0, TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<Integer>builder()
                    .value(100)
                    .variant("variantA")
                    .flagKey("int_flag")
                    .reason(Reason.TARGETING_MATCH.name())
                    .flagMetadata(ImmutableMetadata.builder()
                            .addString("description", "A flag that is always off")
                            .build())
                    .build();
            assertEquals(want, got);
        }

        @DisplayName("Should resolve a valid double flag")
        @SneakyThrows
        @Test
        void shouldResolveAValidDoubleFlag() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.REMOTE)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getDoubleDetails("double_flag", 0.0, TestUtils.defaultEvaluationContext);
            val want = FlagEvaluationDetails.<Double>builder()
                    .value(100.11)
                    .variant("variantA")
                    .flagKey("double_flag")
                    .reason(Reason.TARGETING_MATCH.name())
                    .flagMetadata(ImmutableMetadata.builder()
                            .addString("description", "A flag that is always off")
                            .build())
                    .build();
            assertEquals(want, got);
        }

        @DisplayName("Should resolve a valid object flag")
        @SneakyThrows
        @Test
        void shouldResolveAValidObjectFlag() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.REMOTE)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            val got = client.getObjectDetails("object_flag", new Value("default"), TestUtils.defaultEvaluationContext);

            val want = FlagEvaluationDetails.<Value>builder()
                    .value(new Value(new MutableStructure().add("name", "foo").add("age", 100)))
                    .variant("variantA")
                    .flagKey("object_flag")
                    .reason(Reason.TARGETING_MATCH.name())
                    .flagMetadata(ImmutableMetadata.builder()
                            .addString("description", "A flag that is always off")
                            .build())
                    .build();
            assertEquals(want, got);
        }
    }

    @Nested
    class Tracking {
        @DisplayName("Should send the evaluation information to the data collector")
        @SneakyThrows
        @Test
        void shouldSendTrackingEventToTheDataCollector() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .flushIntervalMs(100L)
                            .maxPendingEvents(1000)
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            client.track(
                    "my-key",
                    TestUtils.defaultEvaluationContext,
                    new MutableTrackingEventDetails().add("revenue", 123).add("user_id", "123ABC"));
            Thread.sleep(200L);
            assertEquals(1, goffAPIMock.getCollectorRequestsHistory().size());
        }

        @DisplayName("Should omit events if max pending events is reached")
        @SneakyThrows
        @Test
        void shouldCallMultipleTimeTheDataCollectorIfMaxPendingEventsIsReached() {
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(
                    GoFeatureFlagProviderOptions.builder()
                            .flushIntervalMs(100L)
                            .maxPendingEvents(1)
                            .endpoint(baseUrl.toString())
                            .evaluationType(EvaluationType.IN_PROCESS)
                            .build()
            );
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);
            client.track(
                    "my-key",
                    TestUtils.defaultEvaluationContext,
                    new MutableTrackingEventDetails().add("revenue", 123).add("user_id", "123ABC"));
            client.track(
                    "my-key",
                    TestUtils.defaultEvaluationContext,
                    new MutableTrackingEventDetails().add("revenue", 567).add("user_id", "123ABC"));
            Thread.sleep(180L);
            assertEquals(2, goffAPIMock.getCollectorRequestsHistory().size());
        }
    }
}
