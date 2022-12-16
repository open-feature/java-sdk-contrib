package dev.openfeature.contrib.providers.flagsmith;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FlagsmithProviderTest {

    public static MockWebServer mockFlagsmithClient;

    final QueueDispatcher dispatcher = new QueueDispatcher() {
        @SneakyThrows
        @Override
        public MockResponse dispatch(RecordedRequest request) {
            if (request.getPath().startsWith("/flags/")) {
                return new MockResponse()
                    .setBody(readMockResponse("environment_flags_response.json"))
                    .addHeader("Content-Type", "application/json");
            }
            return new MockResponse().setResponseCode(404);
        }
    };

    private static Stream<Arguments> provideKeysForFlagResolution() {
        return Stream.of(
            Arguments.of("true_key", "getBooleanEvaluation", Boolean.class, "true"),
            Arguments.of("false_key", "getBooleanEvaluation", Boolean.class, "false")
        );
    }

    @BeforeEach
    void setUp() throws IOException {
        mockFlagsmithClient = new MockWebServer();
        mockFlagsmithClient.setDispatcher(this.dispatcher);
        mockFlagsmithClient.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockFlagsmithClient.shutdown();
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideKeysForFlagResolution")
    void shouldResolveFlagCorrectlyWithCorrectFlagType(
        String key, String methodName, Class<?> expectedType, String flagsmithResult) {
        // Given
        FlagsmithProviderOptions options = FlagsmithProviderOptions.builder()
                                                                   .apiKey("API_KEY")
                                                                   .baseUri(String
                                                                       .format("http://localhost:%s/$s",
                                                                           mockFlagsmithClient
                                                                               .getPort()))
                                                                   .build();
        FlagsmithProvider flagsmithProvider = new FlagsmithProvider(options);

        Object result = null;
        EvaluationContext evaluationContext = new MutableContext();

        // When
        Method method = flagsmithProvider.getClass()
                                         .getMethod(methodName, String.class, expectedType, EvaluationContext.class);
        result = method.invoke(flagsmithProvider, key, null, evaluationContext);

        // Then
        ProviderEvaluation<Object> evaluation = (ProviderEvaluation<Object>) result;
        assertEquals(flagsmithResult, evaluation.getValue());
        assertNull(evaluation.getErrorCode());
    }

    @Test
    void shouldNotResolveFlagIfFlagIsInactiveInFlagsmithInsteadUsingDefaultValue() {
        // Given
        FlagsmithProviderOptions options = FlagsmithProviderOptions.builder().build();
        FlagsmithProvider flagsmithProvider = new FlagsmithProvider(options);
        return;
    }

    private String readMockResponse(String filename) throws IOException {
        String file = getClass().getClassLoader().getResource("mock_responses/" + filename)
                                .getFile();
        byte[] bytes = Files.readAllBytes(Paths.get(file));
        return new String(bytes);
    }
}
