package dev.openfeature.contrib.providers.flipt;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.InvalidContextError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import io.flipt.api.FliptClient;
import io.flipt.api.FliptClient.FliptClientBuilder;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** FliptProvider test, based on APIs mocking. */
@WireMockTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FliptProviderTest {

    public static final String FLAG_NAME = "variant-flag";
    public static final String VARIANT_FLAG_NAME = "variant-flag";
    public static final String VARIANT_FLAG_VALUE = "v1";
    public static final String INT_FLAG_NAME = "int-flag";
    public static final Integer INT_FLAG_VALUE = 123;
    public static final String DOUBLE_FLAG_NAME = "double-flag";
    public static final Double DOUBLE_FLAG_VALUE = 1.23;
    public static final String USERS_FLAG_NAME = "users-flag";
    public static final String TARGETING_KEY = "targeting_key";
    public static final String OBJECT_FLAG_NAME = "object-flag";

    private static FliptProvider fliptProvider;
    private static Client client;
    private String apiUrl;

    @BeforeAll
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        apiUrl = "http://localhost:" + wmRuntimeInfo.getHttpPort();
        fliptProvider = buildFliptProvider();
        OpenFeatureAPI.getInstance().setProviderAndWait("sync", fliptProvider);
        client = OpenFeatureAPI.getInstance().getClient("sync");
    }

    @AfterAll
    public void shutdown() {
        fliptProvider.shutdown();
    }

    private void mockFliptAPI(String url, String resourceName, String flagKey) {
        stubFor(post(urlEqualTo(url))
                .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
                .withRequestBody(WireMock.containing(flagKey))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBody(readResourceFileContent(resourceName))));
    }

    private void mockFliptError(String url, String resourceName, String flagKey, int status) {
        stubFor(post(urlEqualTo(url))
                .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
                .withRequestBody(WireMock.containing(flagKey))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBody(readResourceFileContent(resourceName))));
    }

    @SneakyThrows
    private FliptProvider buildFliptProvider() {
        FliptClientBuilder fliptClientBuilder = FliptClient.builder().url(apiUrl);
        FliptProviderConfig fliptProviderConfig = FliptProviderConfig.builder()
                .fliptClientBuilder(fliptClientBuilder)
                .namespace("default")
                .build();
        return new FliptProvider(fliptProviderConfig);
    }

    @SneakyThrows
    private String readResourceFileContent(String name) {
        URL url = getClass().getResource("/" + name);
        return new String(Files.readAllBytes(Paths.get(url.toURI())));
    }

    private MutableContext context() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        return evaluationContext;
    }

    @Test
    void getBooleanEvaluation() {
        mockFliptAPI("/evaluate/v1/boolean", "boolean.json", FLAG_NAME);
        MutableContext evaluationContext = context();

        assertEquals(true, client.getBooleanValue(FLAG_NAME, false, evaluationContext));
        assertEquals(false, client.getBooleanValue("non-existing", false, evaluationContext));

        FlagEvaluationDetails<Boolean> details = client.getBooleanDetails(FLAG_NAME, false, evaluationContext);
        assertEquals(Reason.STATIC.toString(), details.getReason());
        assertNull(details.getErrorCode());
    }

    @Test
    void getStringVariantEvaluation() {
        mockFliptAPI("/evaluate/v1/variant", "variant.json", VARIANT_FLAG_NAME);
        MutableContext evaluationContext = context();

        assertEquals(
                VARIANT_FLAG_VALUE,
                fliptProvider
                        .getStringEvaluation(VARIANT_FLAG_NAME, "", evaluationContext)
                        .getValue());
        assertEquals(VARIANT_FLAG_VALUE, client.getStringValue(VARIANT_FLAG_NAME, "", evaluationContext));
        assertEquals("fallback_str", client.getStringValue("non-existing", "fallback_str", evaluationContext));
    }

    @Test
    void getStringVariantEvaluation_usesServerDefaultVariantWhenNoMatch() {
        // Flipt returns match=false but still returns a variantKey (server default variant).
        mockFliptAPI("/evaluate/v1/variant", "variant-default-nomatch.json", VARIANT_FLAG_NAME);
        MutableContext evaluationContext = context();

        // SDK defaultValue should NOT be used in this case; server default variant should win.
        ProviderEvaluation<String> evaluation =
                fliptProvider.getStringEvaluation(VARIANT_FLAG_NAME, "sdk-fallback", evaluationContext);

        assertEquals("server-default", evaluation.getValue());
        assertEquals("server-default", evaluation.getVariant());
        assertEquals(Reason.STATIC.toString(), evaluation.getReason());

        // Also verify the OpenFeature client path.
        assertEquals("server-default", client.getStringValue(VARIANT_FLAG_NAME, "sdk-fallback", evaluationContext));
    }

    @Test
    void getIntegerEvaluation() {
        mockFliptAPI("/evaluate/v1/variant", "variant-int.json", INT_FLAG_NAME);
        MutableContext evaluationContext = context();
        evaluationContext.add("userId", "int");

        assertEquals(
                INT_FLAG_VALUE,
                fliptProvider
                        .getIntegerEvaluation(INT_FLAG_NAME, 1, evaluationContext)
                        .getValue());
        assertEquals(INT_FLAG_VALUE, client.getIntegerValue(INT_FLAG_NAME, 1, evaluationContext));
        assertEquals(1, client.getIntegerValue("non-existing", 1, evaluationContext));

        // A non-number flag value is a type mismatch, not a silent fallback.
        mockFliptAPI("/evaluate/v1/variant", "variant.json", VARIANT_FLAG_NAME);
        FlagEvaluationDetails<Integer> wrongType = client.getIntegerDetails(VARIANT_FLAG_NAME, 1, evaluationContext);
        assertEquals(1, wrongType.getValue());
        assertEquals(ErrorCode.TYPE_MISMATCH, wrongType.getErrorCode());
    }

    @Test
    void getDoubleEvaluation() {
        mockFliptAPI("/evaluate/v1/variant", "variant-double.json", DOUBLE_FLAG_NAME);
        MutableContext evaluationContext = context();
        evaluationContext.add("userId", "double");

        assertEquals(
                DOUBLE_FLAG_VALUE,
                fliptProvider
                        .getDoubleEvaluation(DOUBLE_FLAG_NAME, 1.1, evaluationContext)
                        .getValue());
        assertEquals(DOUBLE_FLAG_VALUE, client.getDoubleValue(DOUBLE_FLAG_NAME, 1.1, evaluationContext));
        assertEquals(1.1, client.getDoubleValue("non-existing", 1.1, evaluationContext));

        // A non-number flag value is a type mismatch, not a silent fallback.
        mockFliptAPI("/evaluate/v1/variant", "variant.json", VARIANT_FLAG_NAME);
        FlagEvaluationDetails<Double> wrongType = client.getDoubleDetails(VARIANT_FLAG_NAME, 1.1, evaluationContext);
        assertEquals(1.1, wrongType.getValue());
        assertEquals(ErrorCode.TYPE_MISMATCH, wrongType.getErrorCode());
    }

    @Test
    void getStringEvaluationByUser() {
        mockFliptAPI("/evaluate/v1/variant", "variant.json", "111");
        MutableContext evaluationContext = context();
        evaluationContext.add("userId", "111");

        assertEquals(
                VARIANT_FLAG_VALUE,
                fliptProvider
                        .getStringEvaluation(USERS_FLAG_NAME, "", evaluationContext)
                        .getValue());
        assertEquals(VARIANT_FLAG_VALUE, client.getStringValue(USERS_FLAG_NAME, "", evaluationContext));
        evaluationContext.add("userId", "2");
        assertEquals("", client.getStringValue(USERS_FLAG_NAME, "", evaluationContext));
    }

    @Test
    void getEvaluationMetadataTest() {
        mockFliptAPI("/evaluate/v1/variant", "variant.json", VARIANT_FLAG_NAME);
        MutableContext evaluationContext = context();

        ProviderEvaluation<String> stringEvaluation =
                fliptProvider.getStringEvaluation(VARIANT_FLAG_NAME, "", evaluationContext);
        ImmutableMetadata flagMetadata = stringEvaluation.getFlagMetadata();
        assertEquals("attachment-1", flagMetadata.getString("variant-attachment"));

        FlagEvaluationDetails<String> nonExistingFlagEvaluation =
                client.getStringDetails("non-existing", "", evaluationContext);
        assertNull(nonExistingFlagEvaluation.getFlagMetadata().getBoolean("variant-attachment"));
    }

    @SneakyThrows
    @Test
    void getObjectEvaluationTest() {
        mockFliptAPI("/evaluate/v1/variant", "variant-object.json", OBJECT_FLAG_NAME);
        MutableContext evaluationContext = context();
        evaluationContext.add("userId", "object");

        Value expectedValue = Value.objectToValue(Map.of("key1", "value1", "key2", 42, "key3", true));
        Value emptyValue = new Value();

        assertEquals(expectedValue, client.getObjectValue(OBJECT_FLAG_NAME, emptyValue, evaluationContext));
        assertEquals(emptyValue, client.getObjectValue("non-existing", emptyValue, evaluationContext));

        // A flag without a structured attachment falls back to the caller's default.
        mockFliptAPI("/evaluate/v1/variant", "variant.json", VARIANT_FLAG_NAME);
        assertEquals(emptyValue, client.getObjectValue(VARIANT_FLAG_NAME, emptyValue, evaluationContext));
    }

    @Test
    void emptyStringVariantIsAValue() {
        mockFliptAPI("/evaluate/v1/variant", "variant-empty-string.json", "string-zero-flag");
        MutableContext evaluationContext = context();

        FlagEvaluationDetails<String> details =
                client.getStringDetails("string-zero-flag", "fallback", evaluationContext);
        assertEquals("", details.getValue());
        assertNull(details.getErrorCode());
    }

    @Test
    void disabledVariantUsesTheCallerDefault() {
        mockFliptAPI("/evaluate/v1/variant", "variant-disabled.json", "disabled-string-flag");
        MutableContext evaluationContext = context();

        FlagEvaluationDetails<String> details =
                client.getStringDetails("disabled-string-flag", "bye", evaluationContext);
        assertEquals("bye", details.getValue());
        assertEquals(Reason.DISABLED.toString(), details.getReason());
        assertNull(details.getErrorCode());
    }

    @Test
    void noDefaultVariantUsesTheCallerDefault() {
        mockFliptAPI("/evaluate/v1/variant", "variant-no-default.json", "no-default-flag");
        MutableContext evaluationContext = context();

        FlagEvaluationDetails<String> details =
                client.getStringDetails("no-default-flag", "fallback", evaluationContext);
        assertEquals("fallback", details.getValue());
        assertEquals(Reason.DEFAULT.toString(), details.getReason());
        assertNull(details.getErrorCode());
    }

    @Test
    void disabledBooleanReportsDisabled() {
        mockFliptAPI("/evaluate/v1/boolean", "boolean-disabled.json", "disabled-boolean-flag");
        MutableContext evaluationContext = context();

        FlagEvaluationDetails<Boolean> details =
                client.getBooleanDetails("disabled-boolean-flag", true, evaluationContext);
        assertEquals(false, details.getValue());
        assertEquals(Reason.DISABLED.toString(), details.getReason());
        assertNull(details.getErrorCode());
    }

    @Test
    void missingFlagReportsFlagNotFound() {
        mockFliptError("/evaluate/v1/variant", "error-not-found.json", "missing-flag", 404);
        MutableContext evaluationContext = context();

        FlagEvaluationDetails<String> details = client.getStringDetails("missing-flag", "fallback", evaluationContext);
        assertEquals("fallback", details.getValue());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, details.getErrorCode());
        assertEquals(Reason.ERROR.toString(), details.getReason());
        assertThrows(
                FlagNotFoundError.class,
                () -> fliptProvider.getStringEvaluation("missing-flag", "fallback", evaluationContext));
    }

    @Test
    void wrongFlagTypeReportsTypeMismatch() {
        mockFliptError("/evaluate/v1/boolean", "error-type-mismatch.json", "string-flag", 400);
        MutableContext evaluationContext = context();

        FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("string-flag", false, evaluationContext);
        assertEquals(false, details.getValue());
        assertEquals(ErrorCode.TYPE_MISMATCH, details.getErrorCode());
        assertEquals(Reason.ERROR.toString(), details.getReason());
        assertThrows(
                TypeMismatchError.class,
                () -> fliptProvider.getBooleanEvaluation("string-flag", false, evaluationContext));
    }

    @Test
    void invalidArgumentWithoutATypeMismatchReportsInvalidContext() {
        mockFliptError("/evaluate/v1/variant", "error-invalid-context.json", "invalid-context-flag", 400);
        MutableContext evaluationContext = context();

        FlagEvaluationDetails<String> details =
                client.getStringDetails("invalid-context-flag", "fallback", evaluationContext);
        assertEquals("fallback", details.getValue());
        assertEquals(ErrorCode.INVALID_CONTEXT, details.getErrorCode());
        assertThrows(
                InvalidContextError.class,
                () -> fliptProvider.getStringEvaluation("invalid-context-flag", "fallback", evaluationContext));
    }

    @Test
    void unavailableBackendReportsProviderNotReady() {
        mockFliptError("/evaluate/v1/variant", "error-unavailable.json", "unavailable-flag", 503);
        MutableContext evaluationContext = context();

        FlagEvaluationDetails<String> details =
                client.getStringDetails("unavailable-flag", "fallback", evaluationContext);
        assertEquals("fallback", details.getValue());
        assertEquals(ErrorCode.PROVIDER_NOT_READY, details.getErrorCode());
        assertThrows(
                ProviderNotReadyError.class,
                () -> fliptProvider.getStringEvaluation("unavailable-flag", "fallback", evaluationContext));
    }

    @Test
    void booleanTargetingRuleReportsTargetingMatch() {
        mockFliptAPI("/evaluate/v1/boolean", "boolean-targeting.json", "targeted-boolean-flag");
        MutableContext evaluationContext = context();

        FlagEvaluationDetails<Boolean> details =
                client.getBooleanDetails("targeted-boolean-flag", false, evaluationContext);
        assertTrue(details.getValue());
        assertEquals(Reason.TARGETING_MATCH.toString(), details.getReason());
        assertNull(details.getErrorCode());
    }

    @Test
    void numericCoercionIsLossless() {
        MutableContext evaluationContext = context();

        // An integral float narrows to an integer without loss.
        mockFliptAPI("/evaluate/v1/variant", "variant-integral-float.json", "integral-float-flag");
        assertEquals(10, client.getIntegerValue("integral-float-flag", 1, evaluationContext));

        // A fractional float does not narrow silently.
        mockFliptAPI("/evaluate/v1/variant", "variant-fractional-float.json", "float-flag");
        FlagEvaluationDetails<Integer> fractional = client.getIntegerDetails("float-flag", 1, evaluationContext);
        assertEquals(1, fractional.getValue());
        assertEquals(ErrorCode.TYPE_MISMATCH, fractional.getErrorCode());

        // An integer widens to a float without loss.
        mockFliptAPI("/evaluate/v1/variant", "variant-int.json", "integer-flag");
        assertEquals(123.0, client.getDoubleValue("integer-flag", 0.1, evaluationContext));
    }
}
