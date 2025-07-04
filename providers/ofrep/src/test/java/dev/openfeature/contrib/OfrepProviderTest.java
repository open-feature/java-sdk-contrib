package dev.openfeature.contrib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.openfeature.contrib.providers.ofrep.OfrepProvider;
import dev.openfeature.contrib.providers.ofrep.OfrepProviderOptions;
import dev.openfeature.contrib.providers.ofrep.internal.OfrepResponse;
import dev.openfeature.contrib.testclasses.OfrepRequestTest;
import dev.openfeature.contrib.testclasses.TestExecutor;
import dev.openfeature.contrib.testclasses.TestProxySelector;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OfrepProviderTest {

    private static final Map<String, Object> FLAG_METADATA = Map.of("flagSetId", "example", "version", "v1");
    private static final ImmutableMetadata FLAG_IMMUTABLE_METADATA = ImmutableMetadata.builder()
            .addString("flagSetId", "example")
            .addString("version", "v1")
            .build();

    private static final String FLAG_KEY = "testFlag";

    private static final String DEFAULT_STRING_VALUE = "defaultValue";
    private static final Boolean DEFAULT_BOOLEAN_VALUE = false;
    private static final int DEFAULT_INT_VALUE = 0;
    private static final double DEFAULT_DOUBLE_VALUE = 0.0;
    private static final Map<String, Object> DEFAULT_OBJECT_VALUE = Map.of();

    private static final String SUCCESSFUL_STRING_VALUE = "successfulValue";
    private static final boolean SUCCESSFUL_BOOLEAN_VALUE = true;
    private static final int SUCCESSFUL_INT_VALUE = 42;
    private static final double SUCCESSFUL_DOUBLE_VALUE = 3.14;
    private static final Map<String, Object> SUCCESSFUL_OBJECT_VALUE = Map.of("object1", Map.of("key", "val"));

    private static final String SUCCESSFUL_VARIANT = "variant";
    private static final String SUCCESSFUL_REASON = "TARGETTING_MATCH";

    private static final String ERROR_CODE_FLAG_NOT_FOUND = ErrorCode.FLAG_NOT_FOUND.toString();
    private static final String ERROR_CODE_TYPE_MISMATCH = ErrorCode.TYPE_MISMATCH.toString();
    private static final String ERROR_CODE_INVALID_CONTEXT = ErrorCode.INVALID_CONTEXT.toString();
    private static final String ERROR_CODE_GENERAL = ErrorCode.GENERAL.toString();

    private static final String ERROR_DETAIL_FLAG_NOT_FOUND = "flag: testFlag not found";
    private static final String ERROR_DETAIL_TYPE_MISMATCH = "Type mismatch: expected Boolean but got String";
    private static final String ERROR_DETAIL_INVALID_CONTEXT = "invalid context for flag: testFlag";
    private static final String ERROR_DETAIL_GENERAL_AUTH = "authentication/authorization error for flag: testFlag";
    private static final String ERROR_DETAIL_RATE_LIMIT_WITH_RETRY_AFTER =
            "Rate limit exceeded for flag: testFlag, retry after: ";
    private static final String ERROR_DETAIL_RATE_LIMIT_TRY_AGAIN =
            "general error for flag: " + FLAG_KEY + "; Rate limit exceeded. Please wait before making another request.";
    private static final String ERROR_DETAIL_GENERAL_HTTP_TIMEOUT =
            "general error for flag: " + FLAG_KEY + "; IO error: HTTP connect timed out";

    private static final String HEADER_AUTH_KEY = "Authorization";
    private static final String HEADER_AUTH_VALUE = "Bearer token";

    private static final String HEADER_CUSTOM_KEY = "Custom-Header";
    private static final String HEADER_CUSTOM_VALUE = "Custom-Value";

    private static final String CONTEXT_COLOR_KEY = "color";
    private static final String CONTEXT_COLOR_VALUE = "yellow";

    private static final String CONTEXT_EMAIL_KEY = "email";
    private static final String CONTEXT_EMAIL_VALUE = "someone@example.com";

    private static final TestProxySelector proxySelector = new TestProxySelector();
    private static final TestExecutor executor = new TestExecutor();

    private static final ObjectMapper serializer = new ObjectMapper();
    private static final ObjectMapper deserializer =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final OfrepProvider ofrepProviderWithRequestTimeout =
            OfrepProvider.constructProvider(OfrepProviderOptions.builder()
                    .baseUrl("http://10.255.255.1")
                    .requestTimeout(Duration.ofSeconds(2))
                    .build());

    private static final OfrepProvider ofrepProviderWithConnectTimeout =
            OfrepProvider.constructProvider(OfrepProviderOptions.builder()
                    .baseUrl("http://10.255.255.1")
                    .connectTimeout(Duration.ofSeconds(2))
                    .build());

    private static MockWebServer mockWebServer;

    private static OfrepProvider ofrepProvider;
    private static OfrepProvider ofrepProviderWithProxy;
    private static OfrepProvider ofrepProviderWithExecutor;

    private static EvaluationContext context;

    @BeforeEach
    void setUpServer() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();

        ImmutableMap<String, ImmutableList<String>> headers = ImmutableMap.of(
                HEADER_AUTH_KEY, ImmutableList.of(HEADER_AUTH_VALUE),
                HEADER_CUSTOM_KEY, ImmutableList.of(HEADER_CUSTOM_VALUE));

        Map<String, Value> content = new HashMap<>();
        content.put(CONTEXT_COLOR_KEY, new Value(CONTEXT_COLOR_VALUE));
        content.put(CONTEXT_EMAIL_KEY, new Value(CONTEXT_EMAIL_VALUE));

        context = new ImmutableContext(content);

        ofrepProvider = OfrepProvider.constructProvider(
                OfrepProviderOptions.builder().baseUrl(baseUrl).headers(headers).build());

        ofrepProviderWithProxy = OfrepProvider.constructProvider(OfrepProviderOptions.builder()
                .baseUrl(baseUrl)
                .proxySelector(proxySelector)
                .build());

        ofrepProviderWithExecutor = OfrepProvider.constructProvider(OfrepProviderOptions.builder()
                .baseUrl(baseUrl)
                .executor(executor)
                .build());
    }

    @AfterEach
    void shutDownServer() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void testHeaderFlow() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setBody("{}").setResponseCode(200));

        ofrepProvider.getStringEvaluation(FLAG_KEY, DEFAULT_STRING_VALUE, new ImmutableContext());

        RecordedRequest sentRequest = mockWebServer.takeRequest();

        assertTrue(HEADER_AUTH_VALUE.equals(sentRequest.getHeader(HEADER_AUTH_KEY)));
        assertTrue(HEADER_CUSTOM_VALUE.equals(sentRequest.getHeader(HEADER_CUSTOM_KEY)));
    }

    @Test
    void testEvaluationContextFlow() throws InterruptedException, IOException {
        mockWebServer.enqueue(new MockResponse().setBody("{}").setResponseCode(200));

        ofrepProvider.getStringEvaluation(FLAG_KEY, DEFAULT_STRING_VALUE, context);

        RecordedRequest sentRequest = mockWebServer.takeRequest();

        OfrepRequestTest requestBody =
                deserializer.readValue(sentRequest.getBody().readByteArray(), OfrepRequestTest.class);
        String contextColor = (String) requestBody.getContext().get(CONTEXT_COLOR_KEY);
        String contextEmail = (String) requestBody.getContext().get(CONTEXT_EMAIL_KEY);

        assertTrue(CONTEXT_COLOR_VALUE.equals(contextColor));
        assertTrue(CONTEXT_EMAIL_VALUE.equals(contextEmail));
    }

    @Test
    void testSuccessfulStringResponse() throws JsonProcessingException {
        OfrepResponse successfulStringResponse = new OfrepResponse();
        successfulStringResponse.setKey(FLAG_KEY);
        successfulStringResponse.setValue(SUCCESSFUL_STRING_VALUE);
        successfulStringResponse.setReason(SUCCESSFUL_REASON);
        successfulStringResponse.setVariant(SUCCESSFUL_VARIANT);
        successfulStringResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(successfulStringResponse))
                .setResponseCode(200));

        ProviderEvaluation<String> evaluation =
                ofrepProvider.getStringEvaluation(FLAG_KEY, DEFAULT_STRING_VALUE, new ImmutableContext());

        assertTrue(SUCCESSFUL_STRING_VALUE.equals(evaluation.getValue()));
        assertTrue(SUCCESSFUL_VARIANT.equals(evaluation.getVariant()));
        assertTrue(SUCCESSFUL_REASON.equals(evaluation.getReason()));
        assertTrue(FLAG_IMMUTABLE_METADATA.equals(evaluation.getFlagMetadata()));
    }

    @Test
    void testSuccessfulBooleanResponse() throws JsonProcessingException {
        OfrepResponse successfulBooleanResponse = new OfrepResponse();
        successfulBooleanResponse.setKey(FLAG_KEY);
        successfulBooleanResponse.setValue(SUCCESSFUL_BOOLEAN_VALUE);
        successfulBooleanResponse.setReason(SUCCESSFUL_REASON);
        successfulBooleanResponse.setVariant(SUCCESSFUL_VARIANT);
        successfulBooleanResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(successfulBooleanResponse))
                .setResponseCode(200));

        ProviderEvaluation<Boolean> evaluation =
                ofrepProvider.getBooleanEvaluation(FLAG_KEY, DEFAULT_BOOLEAN_VALUE, new ImmutableContext());

        assertEquals(SUCCESSFUL_BOOLEAN_VALUE, evaluation.getValue());
        assertTrue(SUCCESSFUL_VARIANT.equals(evaluation.getVariant()));
        assertTrue(SUCCESSFUL_REASON.equals(evaluation.getReason()));
        assertTrue(FLAG_IMMUTABLE_METADATA.equals(evaluation.getFlagMetadata()));
    }

    @Test
    void testSuccessfulIntResponse() throws JsonProcessingException {
        OfrepResponse successfulIntResponse = new OfrepResponse();
        successfulIntResponse.setKey(FLAG_KEY);
        successfulIntResponse.setValue(SUCCESSFUL_INT_VALUE);
        successfulIntResponse.setReason(SUCCESSFUL_REASON);
        successfulIntResponse.setVariant(SUCCESSFUL_VARIANT);
        successfulIntResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(successfulIntResponse))
                .setResponseCode(200));

        ProviderEvaluation<Integer> evaluation =
                ofrepProvider.getIntegerEvaluation(FLAG_KEY, DEFAULT_INT_VALUE, new ImmutableContext());

        assertEquals(SUCCESSFUL_INT_VALUE, evaluation.getValue());
        assertTrue(SUCCESSFUL_VARIANT.equals(evaluation.getVariant()));
        assertTrue(SUCCESSFUL_REASON.equals(evaluation.getReason()));
        assertTrue(FLAG_IMMUTABLE_METADATA.equals(evaluation.getFlagMetadata()));
    }

    @Test
    void testSuccessfulDoubleResponse() throws JsonProcessingException {
        OfrepResponse successfulDoubleResponse = new OfrepResponse();
        successfulDoubleResponse.setKey(FLAG_KEY);
        successfulDoubleResponse.setValue(SUCCESSFUL_DOUBLE_VALUE);
        successfulDoubleResponse.setReason(SUCCESSFUL_REASON);
        successfulDoubleResponse.setVariant(SUCCESSFUL_VARIANT);
        successfulDoubleResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(successfulDoubleResponse))
                .setResponseCode(200));

        ProviderEvaluation<Double> evaluation =
                ofrepProvider.getDoubleEvaluation(FLAG_KEY, DEFAULT_DOUBLE_VALUE, new ImmutableContext());

        assertEquals(SUCCESSFUL_DOUBLE_VALUE, evaluation.getValue());
        assertTrue(SUCCESSFUL_VARIANT.equals(evaluation.getVariant()));
        assertTrue(SUCCESSFUL_REASON.equals(evaluation.getReason()));
        assertTrue(FLAG_IMMUTABLE_METADATA.equals(evaluation.getFlagMetadata()));
    }

    @Test
    void testSuccessfulObjectResponse() throws JsonProcessingException {
        OfrepResponse successfulObjectResponse = new OfrepResponse();
        successfulObjectResponse.setKey(FLAG_KEY);
        successfulObjectResponse.setValue(SUCCESSFUL_OBJECT_VALUE);
        successfulObjectResponse.setReason(SUCCESSFUL_REASON);
        successfulObjectResponse.setVariant(SUCCESSFUL_VARIANT);
        successfulObjectResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(successfulObjectResponse))
                .setResponseCode(200));

        ProviderEvaluation<Value> evaluation = ofrepProvider.getObjectEvaluation(
                FLAG_KEY, Value.objectToValue(DEFAULT_OBJECT_VALUE), new ImmutableContext());

        assertTrue(SUCCESSFUL_OBJECT_VALUE.equals(
                evaluation.getValue().asStructure().asObjectMap()));
        assertTrue(SUCCESSFUL_VARIANT.equals(evaluation.getVariant()));
        assertTrue(SUCCESSFUL_REASON.equals(evaluation.getReason()));
        assertTrue(FLAG_IMMUTABLE_METADATA.equals(evaluation.getFlagMetadata()));
    }

    @Test
    void testFlagNotFoundResponse() throws JsonProcessingException {
        OfrepResponse flagNotFoundResponse = new OfrepResponse();
        flagNotFoundResponse.setKey(FLAG_KEY);
        flagNotFoundResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(flagNotFoundResponse))
                .setResponseCode(404));

        ProviderEvaluation<String> evaluation =
                ofrepProvider.getStringEvaluation(FLAG_KEY, DEFAULT_STRING_VALUE, new ImmutableContext());

        assertTrue(DEFAULT_STRING_VALUE.equals(evaluation.getValue()));
        assertNull(evaluation.getVariant());
        assertNull(evaluation.getReason());
        assertTrue(FLAG_IMMUTABLE_METADATA.equals(evaluation.getFlagMetadata()));
        assertTrue(ERROR_CODE_FLAG_NOT_FOUND.equals(evaluation.getErrorCode().toString()));
        assertTrue(ERROR_DETAIL_FLAG_NOT_FOUND.equals(evaluation.getErrorMessage()));
    }

    @Test
    void testTypeMismatch() throws JsonProcessingException {
        OfrepResponse typeMismatchResponse = new OfrepResponse();
        typeMismatchResponse.setKey(FLAG_KEY);
        typeMismatchResponse.setValue(
                SUCCESSFUL_STRING_VALUE); // Intentionally returning a string value to get a type mismatch
        typeMismatchResponse.setReason(SUCCESSFUL_REASON);
        typeMismatchResponse.setVariant(SUCCESSFUL_VARIANT);
        typeMismatchResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(typeMismatchResponse))
                .setResponseCode(200));

        ProviderEvaluation<Boolean> evaluation =
                ofrepProvider.getBooleanEvaluation(FLAG_KEY, DEFAULT_BOOLEAN_VALUE, new ImmutableContext());

        assertTrue(DEFAULT_BOOLEAN_VALUE.equals(evaluation.getValue()));
        assertNull(evaluation.getVariant());
        assertNull(evaluation.getReason());
        assertTrue(FLAG_IMMUTABLE_METADATA.equals(evaluation.getFlagMetadata()));
        assertTrue(ERROR_CODE_TYPE_MISMATCH.equals(evaluation.getErrorCode().toString()));
        assertTrue(ERROR_DETAIL_TYPE_MISMATCH.equals(evaluation.getErrorMessage()));
    }

    @Test
    void testInvalidContext() throws JsonProcessingException {
        OfrepResponse invalidContextResponse = new OfrepResponse();
        invalidContextResponse.setKey(FLAG_KEY);
        invalidContextResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(invalidContextResponse))
                .setResponseCode(400));

        ProviderEvaluation<String> evaluation =
                ofrepProvider.getStringEvaluation(FLAG_KEY, DEFAULT_STRING_VALUE, new ImmutableContext());

        assertTrue(DEFAULT_STRING_VALUE.equals(evaluation.getValue()));
        assertNull(evaluation.getVariant());
        assertNull(evaluation.getReason());
        assertTrue(FLAG_IMMUTABLE_METADATA.equals(evaluation.getFlagMetadata()));
        assertTrue(ERROR_CODE_INVALID_CONTEXT.equals(evaluation.getErrorCode().toString()));
        assertTrue(ERROR_DETAIL_INVALID_CONTEXT.equals(evaluation.getErrorMessage()));
    }

    @Test
    void testGeneralUnauthorizedError() throws JsonProcessingException {
        OfrepResponse generalAuthErrorResponse = new OfrepResponse();
        generalAuthErrorResponse.setKey(FLAG_KEY);
        generalAuthErrorResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(generalAuthErrorResponse))
                .setResponseCode(401));

        ProviderEvaluation<String> evaluation =
                ofrepProvider.getStringEvaluation(FLAG_KEY, DEFAULT_STRING_VALUE, new ImmutableContext());

        assertTrue(DEFAULT_STRING_VALUE.equals(evaluation.getValue()));
        assertNull(evaluation.getVariant());
        assertNull(evaluation.getReason());
        assertTrue(FLAG_IMMUTABLE_METADATA.equals(evaluation.getFlagMetadata()));
        assertTrue(ERROR_CODE_GENERAL.equals(evaluation.getErrorCode().toString()));
        assertTrue(ERROR_DETAIL_GENERAL_AUTH.equals(evaluation.getErrorMessage()));
    }

    @Test
    void testGeneralForbiddenError() throws JsonProcessingException {
        OfrepResponse generalAuthErrorResponse = new OfrepResponse();
        generalAuthErrorResponse.setKey(FLAG_KEY);
        generalAuthErrorResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(generalAuthErrorResponse))
                .setResponseCode(403));

        ProviderEvaluation<String> evaluation =
                ofrepProvider.getStringEvaluation(FLAG_KEY, DEFAULT_STRING_VALUE, new ImmutableContext());

        assertTrue(DEFAULT_STRING_VALUE.equals(evaluation.getValue()));
        assertNull(evaluation.getVariant());
        assertNull(evaluation.getReason());
        assertTrue(FLAG_IMMUTABLE_METADATA.equals(evaluation.getFlagMetadata()));
        assertTrue(ERROR_CODE_GENERAL.equals(evaluation.getErrorCode().toString()));
        assertTrue(ERROR_DETAIL_GENERAL_AUTH.equals(evaluation.getErrorMessage()));
    }

    @Test
    void testRateLimit() throws JsonProcessingException, InterruptedException {
        ZonedDateTime now = ZonedDateTime.now().plusSeconds(3);
        String nowFormatted = DateTimeFormatter.RFC_1123_DATE_TIME.format(now);

        OfrepResponse rateLimitResponse = new OfrepResponse();
        rateLimitResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(rateLimitResponse))
                .setHeader("Retry-After", nowFormatted)
                .setResponseCode(429));

        ProviderEvaluation<String> evaluation =
                ofrepProvider.getStringEvaluation(FLAG_KEY, DEFAULT_STRING_VALUE, new ImmutableContext());
        ProviderEvaluation<String> evaluationRejectedBySdk =
                ofrepProvider.getStringEvaluation(FLAG_KEY, DEFAULT_STRING_VALUE, new ImmutableContext());

        String errorDetail = ERROR_DETAIL_RATE_LIMIT_WITH_RETRY_AFTER
                + now.toInstant().truncatedTo(ChronoUnit.SECONDS).toString();

        assertTrue(DEFAULT_STRING_VALUE.equals(evaluation.getValue()));
        assertNull(evaluation.getVariant());
        assertNull(evaluation.getReason());
        assertTrue(FLAG_IMMUTABLE_METADATA.equals(evaluation.getFlagMetadata()));
        assertTrue(ERROR_CODE_GENERAL.equals(evaluation.getErrorCode().toString()));
        assertTrue(errorDetail.equals(evaluation.getErrorMessage()));

        assertTrue(DEFAULT_STRING_VALUE.equals(evaluationRejectedBySdk.getValue()));
        assertNull(evaluationRejectedBySdk.getVariant());
        assertNull(evaluationRejectedBySdk.getReason());
        assertTrue(ImmutableMetadata.builder().build().equals(evaluationRejectedBySdk.getFlagMetadata()));
        assertTrue(
                ERROR_CODE_GENERAL.equals(evaluationRejectedBySdk.getErrorCode().toString()));
        assertTrue(ERROR_DETAIL_RATE_LIMIT_TRY_AGAIN.equals(evaluationRejectedBySdk.getErrorMessage()));

        Thread.sleep(3000); // Wait for the rate limit to expire

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(rateLimitResponse))
                .setHeader("Retry-After", nowFormatted)
                .setResponseCode(429));

        ProviderEvaluation<String> evaluationAfterRateLimitExpired =
                ofrepProvider.getStringEvaluation(FLAG_KEY, DEFAULT_STRING_VALUE, new ImmutableContext());

        assertTrue(DEFAULT_STRING_VALUE.equals(evaluationAfterRateLimitExpired.getValue()));
        assertNull(evaluationAfterRateLimitExpired.getVariant());
        assertNull(evaluationAfterRateLimitExpired.getReason());
        assertTrue(FLAG_IMMUTABLE_METADATA.equals(evaluationAfterRateLimitExpired.getFlagMetadata()));
        assertTrue(ERROR_CODE_GENERAL.equals(
                evaluationAfterRateLimitExpired.getErrorCode().toString()));
        assertTrue(errorDetail.equals(evaluationAfterRateLimitExpired.getErrorMessage()));
    }

    @Test
    void testRequestTimeoutConnection() throws JsonProcessingException {
        OfrepResponse successfulStringResponse = new OfrepResponse();
        successfulStringResponse.setKey(FLAG_KEY);
        successfulStringResponse.setValue(SUCCESSFUL_STRING_VALUE);
        successfulStringResponse.setReason(SUCCESSFUL_REASON);
        successfulStringResponse.setVariant(SUCCESSFUL_VARIANT);
        successfulStringResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(successfulStringResponse))
                .setBodyDelay(4, TimeUnit.SECONDS)
                .setResponseCode(200));

        ProviderEvaluation<String> evaluation = ofrepProviderWithRequestTimeout.getStringEvaluation(
                FLAG_KEY, DEFAULT_STRING_VALUE, new ImmutableContext());

        assertTrue(DEFAULT_STRING_VALUE.equals(evaluation.getValue()));
        assertNull(evaluation.getVariant());
        assertNull(evaluation.getReason());
        assertTrue(ImmutableMetadata.builder().build().equals(evaluation.getFlagMetadata()));
        assertTrue(ERROR_CODE_GENERAL.equals(evaluation.getErrorCode().toString()));
        assertTrue(ERROR_DETAIL_GENERAL_HTTP_TIMEOUT.equals(evaluation.getErrorMessage()));
    }

    @Test
    void testConnectTimeoutConnection() throws JsonProcessingException {
        OfrepResponse successfulStringResponse = new OfrepResponse();
        successfulStringResponse.setKey(FLAG_KEY);
        successfulStringResponse.setValue(SUCCESSFUL_STRING_VALUE);
        successfulStringResponse.setReason(SUCCESSFUL_REASON);
        successfulStringResponse.setVariant(SUCCESSFUL_VARIANT);
        successfulStringResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(successfulStringResponse))
                .setBodyDelay(4, TimeUnit.SECONDS)
                .setResponseCode(200));

        ProviderEvaluation<String> evaluation = ofrepProviderWithConnectTimeout.getStringEvaluation(
                FLAG_KEY, DEFAULT_STRING_VALUE, new ImmutableContext());

        assertTrue(DEFAULT_STRING_VALUE.equals(evaluation.getValue()));
        assertNull(evaluation.getVariant());
        assertNull(evaluation.getReason());
        assertTrue(ImmutableMetadata.builder().build().equals(evaluation.getFlagMetadata()));
        assertTrue(ERROR_CODE_GENERAL.equals(evaluation.getErrorCode().toString()));
        assertTrue(ERROR_DETAIL_GENERAL_HTTP_TIMEOUT.equals(evaluation.getErrorMessage()));
    }

    @Test
    void testCustomProxySelector() throws JsonProcessingException {
        OfrepResponse successfulStringResponse = new OfrepResponse();
        successfulStringResponse.setKey(FLAG_KEY);
        successfulStringResponse.setValue(SUCCESSFUL_STRING_VALUE);
        successfulStringResponse.setReason(SUCCESSFUL_REASON);
        successfulStringResponse.setVariant(SUCCESSFUL_VARIANT);
        successfulStringResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(successfulStringResponse))
                .setResponseCode(200));

        ProviderEvaluation<String> evaluation =
                ofrepProviderWithProxy.getStringEvaluation(FLAG_KEY, DEFAULT_STRING_VALUE, new ImmutableContext());

        assertTrue(!proxySelector.getSelectedUris().isEmpty());
        assertTrue(SUCCESSFUL_STRING_VALUE.equals(evaluation.getValue()));
        assertTrue(SUCCESSFUL_VARIANT.equals(evaluation.getVariant()));
        assertTrue(SUCCESSFUL_REASON.equals(evaluation.getReason()));
        assertTrue(FLAG_IMMUTABLE_METADATA.equals(evaluation.getFlagMetadata()));
    }

    @Test
    void testCustomExecutor() throws JsonProcessingException {
        OfrepResponse successfulStringResponse = new OfrepResponse();
        successfulStringResponse.setKey(FLAG_KEY);
        successfulStringResponse.setValue(SUCCESSFUL_STRING_VALUE);
        successfulStringResponse.setReason(SUCCESSFUL_REASON);
        successfulStringResponse.setVariant(SUCCESSFUL_VARIANT);
        successfulStringResponse.setMetadata(FLAG_METADATA);

        mockWebServer.enqueue(new MockResponse()
                .setBody(serializer.writeValueAsString(successfulStringResponse))
                .setResponseCode(200));

        ProviderEvaluation<String> evaluation =
                ofrepProviderWithExecutor.getStringEvaluation(FLAG_KEY, DEFAULT_STRING_VALUE, new ImmutableContext());

        assertTrue(!executor.getTasks().isEmpty());
        assertTrue(SUCCESSFUL_STRING_VALUE.equals(evaluation.getValue()));
        assertTrue(SUCCESSFUL_VARIANT.equals(evaluation.getVariant()));
        assertTrue(SUCCESSFUL_REASON.equals(evaluation.getReason()));
        assertTrue(FLAG_IMMUTABLE_METADATA.equals(evaluation.getFlagMetadata()));
    }

    @Test
    void testConfigurationValidation() {
        Exception exceptionInvalidBaseUrl = assertThrows(IllegalArgumentException.class, () -> {
            OfrepProviderOptions options =
                    OfrepProviderOptions.builder().baseUrl("invalid-url").build();
            OfrepProvider.constructProvider(options);
        });

        Exception exceptionNullBaseUrl = assertThrows(IllegalArgumentException.class, () -> {
            OfrepProviderOptions options =
                    OfrepProviderOptions.builder().baseUrl(null).build();
            OfrepProvider.constructProvider(options);
        });

        Exception exceptionNullHeaders = assertThrows(IllegalArgumentException.class, () -> {
            OfrepProviderOptions options =
                    OfrepProviderOptions.builder().headers(null).build();
            OfrepProvider.constructProvider(options);
        });

        Exception exceptionNegativeConnectTimeout = assertThrows(IllegalArgumentException.class, () -> {
            OfrepProviderOptions options = OfrepProviderOptions.builder()
                    .connectTimeout(Duration.ofSeconds(-10))
                    .build();
            OfrepProvider.constructProvider(options);
        });

        Exception exceptionZeroedConnectTimeout = assertThrows(IllegalArgumentException.class, () -> {
            OfrepProviderOptions options = OfrepProviderOptions.builder()
                    .connectTimeout(Duration.ofSeconds(0))
                    .build();
            OfrepProvider.constructProvider(options);
        });

        Exception exceptionNullConnectTimeout = assertThrows(IllegalArgumentException.class, () -> {
            OfrepProviderOptions options =
                    OfrepProviderOptions.builder().connectTimeout(null).build();
            OfrepProvider.constructProvider(options);
        });

        Exception exceptionNegativeRequestTimeout = assertThrows(IllegalArgumentException.class, () -> {
            OfrepProviderOptions options = OfrepProviderOptions.builder()
                    .requestTimeout(Duration.ofSeconds(-10))
                    .build();
            OfrepProvider.constructProvider(options);
        });

        Exception exceptionZeroedRequestTimeout = assertThrows(IllegalArgumentException.class, () -> {
            OfrepProviderOptions options = OfrepProviderOptions.builder()
                    .requestTimeout(Duration.ofSeconds(0))
                    .build();
            OfrepProvider.constructProvider(options);
        });

        Exception exceptionNullRequestTimeout = assertThrows(IllegalArgumentException.class, () -> {
            OfrepProviderOptions options =
                    OfrepProviderOptions.builder().requestTimeout(null).build();
            OfrepProvider.constructProvider(options);
        });

        Exception exceptionNullProxySelector = assertThrows(IllegalArgumentException.class, () -> {
            OfrepProviderOptions options =
                    OfrepProviderOptions.builder().proxySelector(null).build();
            OfrepProvider.constructProvider(options);
        });

        Exception exceptionNullExecutor = assertThrows(IllegalArgumentException.class, () -> {
            OfrepProviderOptions options =
                    OfrepProviderOptions.builder().executor(null).build();
            OfrepProvider.constructProvider(options);
        });

        assertTrue(exceptionInvalidBaseUrl.getMessage().contains("Invalid base URL"));
        assertTrue(exceptionNullBaseUrl.getMessage().contains("Invalid base URL"));
        assertTrue(exceptionNullHeaders.getMessage().contains("Headers cannot be null"));
        assertTrue(
                exceptionNegativeRequestTimeout.getMessage().contains("Request timeout must be a positive duration"));
        assertTrue(exceptionZeroedRequestTimeout.getMessage().contains("Request timeout must be a positive duration"));
        assertTrue(exceptionNullRequestTimeout.getMessage().contains("Request timeout must be a positive duration"));
        assertTrue(
                exceptionNegativeConnectTimeout.getMessage().contains("Connect timeout must be a positive duration"));
        assertTrue(exceptionZeroedConnectTimeout.getMessage().contains("Connect timeout must be a positive duration"));
        assertTrue(exceptionNullConnectTimeout.getMessage().contains("Connect timeout must be a positive duration"));
        assertTrue(exceptionNullProxySelector.getMessage().contains("ProxySelector cannot be null"));
        assertTrue(exceptionNullExecutor.getMessage().contains("Executor cannot be null"));
    }
}
