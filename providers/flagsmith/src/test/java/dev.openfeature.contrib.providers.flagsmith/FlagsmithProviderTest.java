package dev.openfeature.contrib.providers.flagsmith;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.config.FlagsmithConfig;
import dev.openfeature.contrib.providers.flagsmith.exceptions.InvalidCacheOptionsException;
import dev.openfeature.contrib.providers.flagsmith.exceptions.InvalidOptionsException;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FlagsmithProviderTest {

    public static MockWebServer mockFlagsmithServer;
    public static MockWebServer mockFlagsmithErrorServer;
    public static FlagsmithProvider flagsmithProvider;

    final QueueDispatcher dispatcher = new QueueDispatcher() {
        @SneakyThrows
        @Override
        public MockResponse dispatch(RecordedRequest request) {
            if (request.getPath().startsWith("/flags/")) {
                return new MockResponse()
                        .setBody(readMockResponse("valid_flags_response.json"))
                        .addHeader("Content-Type", "application/json");
            }
            if (request.getPath().startsWith("/identities/")) {
                return new MockResponse()
                        .setBody(readMockResponse("valid_identity_response.json"))
                        .addHeader("Content-Type", "application/json");
            }
            if (request.getPath().startsWith("/environment-document/")) {
                return new MockResponse()
                        .setBody(readMockResponse("environment-document.json"))
                        .addHeader("Content-Type", "application/json");
            }
            return new MockResponse().setResponseCode(404);
        }
    };

    final QueueDispatcher errorDispatcher = new QueueDispatcher() {
        @SneakyThrows
        @Override
        public MockResponse dispatch(RecordedRequest request) {
            return new MockResponse().setResponseCode(500);
        }
    };

    private static Stream<Arguments> provideKeysForFlagResolution() {
        return Stream.of(
                Arguments.of("true_key", "getBooleanEvaluation", Boolean.class, "true"),
                Arguments.of("false_key", "getBooleanEvaluation", Boolean.class, "false"),
                Arguments.of("string_key", "getStringEvaluation", String.class, "string_value"),
                Arguments.of("int_key", "getIntegerEvaluation", Integer.class, "1"),
                Arguments.of("double_key", "getDoubleEvaluation", Double.class, "3.141"),
                Arguments.of("object_key", "getObjectEvaluation", Value.class, "{\"name\":\"json\"}"));
    }

    private static Stream<Arguments> provideDisabledKeysForFlagResolution() {
        return Stream.of(
                Arguments.of("true_key_disabled", "getBooleanEvaluation", Boolean.class, "false"),
                Arguments.of("false_key_disabled", "getBooleanEvaluation", Boolean.class, "true"),
                Arguments.of("string_key_disabled", "getStringEvaluation", String.class, "no_string_value"),
                Arguments.of("int_key_disabled", "getIntegerEvaluation", Integer.class, "2"),
                Arguments.of("double_key_disabled", "getDoubleEvaluation", Double.class, "1.47"),
                Arguments.of("object_key_disabled", "getObjectEvaluation", Value.class, "{\"name\":\"not_json\"}"));
    }

    private static Stream<Arguments> provideBooleanKeysForEnabledFlagResolution() {
        return Stream.of(
                Arguments.of("true_key", "true", null),
                Arguments.of("false_key", "true", null),
                Arguments.of("true_key_disabled", "false", Reason.DISABLED.name()),
                Arguments.of("false_key_disabled", "false", Reason.DISABLED.name()));
    }

    private static Stream<Arguments> invalidOptions() {
        return Stream.of(
                null,
                Arguments.of(FlagsmithProviderOptions.builder().build()),
                Arguments.of(FlagsmithProviderOptions.builder().apiKey("").build()));
    }

    private static Stream<Arguments> invalidCacheOptions() {
        return Stream.of(
                Arguments.of(FlagsmithProviderOptions.builder()
                        .apiKey("API_KEY")
                        .expireCacheAfterAccess(1)
                        .build()),
                Arguments.of(FlagsmithProviderOptions.builder()
                        .apiKey("API_KEY")
                        .maxCacheSize(1)
                        .build()),
                Arguments.of(FlagsmithProviderOptions.builder()
                        .apiKey("API_KEY")
                        .expireCacheAfterWrite(1)
                        .build()),
                Arguments.of(FlagsmithProviderOptions.builder()
                        .apiKey("API_KEY")
                        .recordCacheStats(true)
                        .build()));
    }

    @BeforeEach
    void setUp() throws IOException {
        mockFlagsmithServer = new MockWebServer();
        mockFlagsmithServer.setDispatcher(this.dispatcher);
        mockFlagsmithServer.start();

        // Error server will always result in FlagsmithApiError's used for
        // tests that need to handle this type of error
        mockFlagsmithErrorServer = new MockWebServer();
        mockFlagsmithErrorServer.setDispatcher(this.errorDispatcher);
        mockFlagsmithErrorServer.start();

        FlagsmithProviderOptions options = FlagsmithProviderOptions.builder()
                .apiKey("API_KEY")
                .baseUri(String.format("http://localhost:%s", mockFlagsmithServer.getPort()))
                .usingBooleanConfigValue(true)
                .build();
        flagsmithProvider = new FlagsmithProvider(options);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockFlagsmithServer.shutdown();
        mockFlagsmithErrorServer.shutdown();
    }

    @Test
    void shouldInitializeProviderWhenAllOptionsSet() {
        HashMap<String, String> headers = new HashMap<String, String>() {
            {
                put("header", "string");
            }
        };

        FlagsmithProviderOptions options = FlagsmithProviderOptions.builder()
                .apiKey("ser.API_KEY")
                .baseUri(String.format("http://localhost:%s", mockFlagsmithServer.getPort()))
                .headers(headers)
                .envFlagsCacheKey("CACHE_KEY")
                .expireCacheAfterWriteTimeUnit(TimeUnit.MINUTES)
                .expireCacheAfterWrite(10000)
                .expireCacheAfterAccessTimeUnit(TimeUnit.MINUTES)
                .expireCacheAfterAccess(10000)
                .maxCacheSize(1)
                .recordCacheStats(true)
                .httpInterceptor(null)
                .connectTimeout(10000)
                .writeTimeout(10000)
                .readTimeout(10000)
                .retries(1)
                .localEvaluation(true)
                .environmentRefreshIntervalSeconds(1)
                .enableAnalytics(true)
                .usingBooleanConfigValue(false)
                .supportedProtocols(Collections.singletonList(FlagsmithConfig.Protocol.HTTP_1_1))
                .build();
        assertDoesNotThrow(() -> new FlagsmithProvider(options));
    }

    @Test
    void shouldGetMetadataAndValidateName() {
        assertEquals(
                "Flagsmith Provider",
                new FlagsmithProvider(FlagsmithProviderOptions.builder()
                                .apiKey("API_KEY")
                                .build())
                        .getMetadata()
                        .getName());
    }

    @Test
    void shouldDefaultEnvironmentRefreshIntervalSecondsTo60() {
        FlagsmithProviderOptions options = FlagsmithProviderOptions.builder()
                .apiKey("API_KEY")
                .localEvaluation(true)
                .build();
        assertEquals(Integer.valueOf(60), options.getEnvironmentRefreshIntervalSeconds());
    }

    @ParameterizedTest
    @MethodSource("invalidOptions")
    void shouldThrowAnExceptionWhenOptionsInvalid(FlagsmithProviderOptions options) {
        assertThrows(InvalidOptionsException.class, () -> new FlagsmithProvider(options));
    }

    @ParameterizedTest
    @MethodSource("invalidCacheOptions")
    void shouldThrowAnExceptionWhenCacheOptionsInvalid(FlagsmithProviderOptions options) {
        assertThrows(InvalidCacheOptionsException.class, () -> new FlagsmithProvider(options));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideKeysForFlagResolution")
    void shouldResolveFlagCorrectlyWithCorrectFlagType(
            String key, String methodName, Class<?> expectedType, String flagsmithResult) {
        // Given
        Object result = null;
        EvaluationContext evaluationContext = new MutableContext();

        // When
        Method method =
                flagsmithProvider.getClass().getMethod(methodName, String.class, expectedType, EvaluationContext.class);
        result = method.invoke(flagsmithProvider, key, null, evaluationContext);

        // Then
        ProviderEvaluation<Object> evaluation = (ProviderEvaluation<Object>) result;
        String resultString = getResultString(evaluation.getValue(), expectedType);

        assertEquals(flagsmithResult, resultString);
        assertNull(evaluation.getErrorCode());
        assertNull(evaluation.getReason());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideKeysForFlagResolution")
    void shouldResolveIdentityFlagCorrectlyWithCorrectFlagType(
            String key, String methodName, Class<?> expectedType, String flagsmithResult) {
        // Given
        Object result = null;
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey("my-identity");
        evaluationContext.add("trait1", "value1");

        // When
        Method method =
                flagsmithProvider.getClass().getMethod(methodName, String.class, expectedType, EvaluationContext.class);
        result = method.invoke(flagsmithProvider, key, null, evaluationContext);

        // Then
        ProviderEvaluation<Object> evaluation = (ProviderEvaluation<Object>) result;
        String resultString = getResultString(evaluation.getValue(), expectedType);

        assertEquals(flagsmithResult, resultString);
        assertNull(evaluation.getErrorCode());
        assertNull(evaluation.getReason());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideDisabledKeysForFlagResolution")
    void shouldNotResolveFlagIfFlagIsInactiveInFlagsmithInsteadUsingDefaultValue(
            String key, String methodName, Class<?> expectedType, String defaultValueString) {
        // Given
        Object defaultValue;
        if (expectedType == String.class) {
            defaultValue = defaultValueString;
        } else if (expectedType == Value.class) {
            Map<String, Value> map = new ObjectMapper().readValue(defaultValueString, HashMap.class);
            defaultValue = new Value(new MutableStructure(map));
        } else {
            Method castMethod = expectedType.getMethod("valueOf", String.class);
            defaultValue = castMethod.invoke(expectedType, defaultValueString);
        }

        Object result = null;
        EvaluationContext evaluationContext = new MutableContext();

        // When
        Method method =
                flagsmithProvider.getClass().getMethod(methodName, String.class, expectedType, EvaluationContext.class);
        result = method.invoke(flagsmithProvider, key, defaultValue, evaluationContext);

        // Then
        ProviderEvaluation<Object> evaluation = (ProviderEvaluation<Object>) result;
        String resultString = getResultString(evaluation.getValue(), expectedType);

        assertEquals(defaultValueString, resultString);
        assertNull(evaluation.getErrorCode());
        assertEquals(Reason.DISABLED.name(), evaluation.getReason());
    }

    @Test
    void shouldNotResolveFlagIfExceptionThrownInFlagsmithInsteadUsingDefaultValue() {
        // Given
        String key = "missing_key";
        EvaluationContext evaluationContext = new MutableContext();
        assertThrows(
                FlagNotFoundError.class, () -> flagsmithProvider.getBooleanEvaluation(key, true, new MutableContext()));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideBooleanKeysForEnabledFlagResolution")
    void shouldResolveBooleanFlagUsingEnabledField(String key, String flagsmithResult, String reason) {
        // Given
        FlagsmithProviderOptions options = FlagsmithProviderOptions.builder()
                .apiKey("API_KEY")
                .baseUri(String.format("http://localhost:%s", mockFlagsmithServer.getPort()))
                .build();
        FlagsmithProvider booleanFlagsmithProvider = new FlagsmithProvider(options);

        // When
        ProviderEvaluation<Boolean> result =
                booleanFlagsmithProvider.getBooleanEvaluation(key, true, new MutableContext());

        // Then
        String resultString = getResultString(result.getValue(), Boolean.class);

        assertEquals(flagsmithResult, resultString);
        assertNull(result.getErrorCode());
        assertEquals(reason, result.getReason());
    }

    @Test
    void shouldNotResolveBooleanFlagValueIfFlagsmithErrorThrown() {
        // Given
        FlagsmithProviderOptions options = FlagsmithProviderOptions.builder()
                .apiKey("API_KEY")
                .baseUri(String.format("http://localhost:%s", mockFlagsmithErrorServer.getPort()))
                .usingBooleanConfigValue(false)
                .build();
        FlagsmithProvider booleanFlagsmithProvider = new FlagsmithProvider(options);

        // When
        assertThrows(
                GeneralError.class,
                () -> booleanFlagsmithProvider.getBooleanEvaluation("true_key", false, new MutableContext()));
    }

    @Test
    void shouldNotResolveFlagValueIfFlagsmithErrorThrown() {
        // Given
        FlagsmithProviderOptions options = FlagsmithProviderOptions.builder()
                .apiKey("API_KEY")
                .baseUri(String.format("http://localhost:%s", mockFlagsmithErrorServer.getPort()))
                .usingBooleanConfigValue(true)
                .build();
        FlagsmithProvider booleanFlagsmithProvider = new FlagsmithProvider(options);

        // When
        assertThrows(
                GeneralError.class,
                () -> booleanFlagsmithProvider.getBooleanEvaluation("true_key", false, new MutableContext()));
    }

    private String readMockResponse(String filename) throws IOException {
        String file = getClass()
                .getClassLoader()
                .getResource("mock_responses/" + filename)
                .getFile();
        byte[] bytes = Files.readAllBytes(Paths.get(file));
        return new String(bytes);
    }

    private String getResultString(Object responseValue, Class<?> expectedType) throws JsonProcessingException {
        String resultString = "";
        if (expectedType == Value.class) {
            Value value = (Value) responseValue;
            try {
                Map<String, Object> structure = value.asStructure().asObjectMap();
                return new ObjectMapper().writeValueAsString(structure);
            } catch (ClassCastException cce) {
                Map<String, Value> structure = value.asStructure().asMap();
                return new ObjectMapper().writeValueAsString(structure);
            }
        } else {
            return responseValue.toString();
        }
    }
}
