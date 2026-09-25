package dev.openfeature.contrib.providers.gofeatureflag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.gofeatureflag.util.GoffApiMock;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import lombok.SneakyThrows;
import lombok.val;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Provider remote evaluation")
class ProviderRemoteEvaluationTest extends AbstractGoFeatureFlagProviderTest {
    @DisplayName("Should error if the endpoint is not available")
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
                    .errorMessage("Unknown error while retrieving flag: bool_flag, status code: 500")
                    .build();
            assertEquals(want, got);
        }
    }

    @DisplayName("Should become FATAL on the first evaluation rejected for bad credentials")
    @SneakyThrows
    @Test
    void shouldBecomeFatalOnTheFirstEvaluationRejectedForBadCredentials() {
        try (val s = new MockWebServer()) {
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.INVALID_API_KEY);
            s.setDispatcher(goffAPIMock.dispatcher);
            GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                    .endpoint(s.url("").toString())
                    .evaluationType(EvaluationType.REMOTE)
                    .apiKey("a-rejected-key")
                    .timeout(1000)
                    .build());
            OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
            val client = OpenFeatureAPI.getInstance().getClient(testName);

            // remote evaluation holds no configuration, so initialization has nothing to fetch
            // and cannot discover the credentials are wrong
            assertEquals(ProviderState.READY, client.getProviderState());

            client.getBooleanDetails("bool_flag", false, TestUtils.defaultEvaluationContext);

            // the event is emitted on the SDK's own emitter thread
            for (int i = 0; i < 100 && client.getProviderState() != ProviderState.FATAL; i++) {
                Thread.sleep(20);
            }
            assertEquals(ProviderState.FATAL, client.getProviderState());
        }
    }

    @DisplayName("Should stay READY when an evaluation fails for a repairable reason")
    @SneakyThrows
    @Test
    void shouldStayReadyWhenAnEvaluationFailsForARepairableReason() {
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

            client.getBooleanDetails("bool_flag", false, TestUtils.defaultEvaluationContext);
            Thread.sleep(200);

            // a 500 is repairable without touching the credentials
            assertEquals(ProviderState.READY, client.getProviderState());
        }
    }

    @DisplayName("Should error if no API Key provided")
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
                    .errorMessage("authentication/authorization error for flag: bool_flag")
                    .build();
            assertEquals(want, got);
        }
    }

    @DisplayName("Should error if API Key is invalid")
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
                    .errorMessage("authentication/authorization error for flag: bool_flag")
                    .build();
            assertEquals(want, got);
        }
    }

    @DisplayName("Should error if the flag is not found")
    @SneakyThrows
    @Test
    void shouldErrorIfFlagNotFound() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.REMOTE)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        val got = client.getBooleanDetails("does-not-exists", false, TestUtils.defaultEvaluationContext);
        val want = FlagEvaluationDetails.<Boolean>builder()
                .value(false)
                .flagKey("does-not-exists")
                .reason(Reason.ERROR.name())
                .errorCode(ErrorCode.FLAG_NOT_FOUND)
                .errorMessage("flag: does-not-exists not found")
                .build();
        assertEquals(want, got);
    }

    @DisplayName("Should error if evaluating the wrong type")
    @SneakyThrows
    @Test
    void shouldErrorIfEvaluatingTheWrongType() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.REMOTE)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        val got = client.getStringDetails("bool_flag", "default", TestUtils.defaultEvaluationContext);
        val want = FlagEvaluationDetails.<String>builder()
                .value("default")
                .flagKey("bool_flag")
                .reason(Reason.ERROR.name())
                .errorMessage("Type mismatch: expected String but got Boolean")
                .errorCode(ErrorCode.TYPE_MISMATCH)
                .flagMetadata(ImmutableMetadata.builder()
                        .addString("description", "A flag that is always off")
                        .addBoolean("gofeatureflag_cacheable", true)
                        .build())
                .build();
        assertEquals(want, got);
    }

    @DisplayName("Should resolve a valid boolean flag")
    @SneakyThrows
    @Test
    void shouldResolveAValidBooleanFlag() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.REMOTE)
                .build());
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
                        .addBoolean("gofeatureflag_cacheable", true)
                        .build())
                .build();
        assertEquals(want, got);
    }

    @DisplayName("Should resolve a valid string flag")
    @SneakyThrows
    @Test
    void shouldResolveAValidStringFlag() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.REMOTE)
                .build());
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
                        .addBoolean("gofeatureflag_cacheable", true)
                        .build())
                .build();
        assertEquals(want, got);
    }

    @DisplayName("Should resolve a valid int flag")
    @SneakyThrows
    @Test
    void shouldResolveAValidIntFlag() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.REMOTE)
                .build());
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
                        .addBoolean("gofeatureflag_cacheable", true)
                        .build())
                .build();
        assertEquals(want, got);
    }

    @DisplayName("Should resolve a valid double flag")
    @SneakyThrows
    @Test
    void shouldResolveAValidDoubleFlag() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.REMOTE)
                .build());
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
                        .addBoolean("gofeatureflag_cacheable", true)
                        .build())
                .build();
        assertEquals(want, got);
    }

    @DisplayName("Should resolve a valid object flag")
    @SneakyThrows
    @Test
    void shouldResolveAValidObjectFlag() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.REMOTE)
                .build());
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
                        .addBoolean("gofeatureflag_cacheable", true)
                        .build())
                .build();
        assertEquals(want, got);
    }
}
