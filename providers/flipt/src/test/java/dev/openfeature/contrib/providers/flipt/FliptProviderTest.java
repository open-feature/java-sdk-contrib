package dev.openfeature.contrib.providers.flipt;

import io.flipt.api.FliptClient;
import io.flipt.api.FliptClient.FliptClientBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.*;

/**
 * FliptProvider test, based on APIs mocking.
 */
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
        stubFor(
                post(urlEqualTo(url))
                        .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
                        .withRequestBody(WireMock.containing(flagKey))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
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

    @Test
    void getBooleanEvaluation() {
        mockFliptAPI("/evaluate/v1/boolean", "boolean.json", FLAG_NAME);
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        assertEquals(true, client.getBooleanValue(FLAG_NAME, false, evaluationContext));
        assertEquals(false, client.getBooleanValue("non-existing", false, evaluationContext));
    }

    @Test
    void getStringVariantEvaluation() {
        mockFliptAPI("/evaluate/v1/variant", "variant.json", VARIANT_FLAG_NAME);
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        assertEquals(VARIANT_FLAG_VALUE, fliptProvider.getStringEvaluation(VARIANT_FLAG_NAME, "",
                evaluationContext).getValue());
        assertEquals(VARIANT_FLAG_VALUE, client.getStringValue(VARIANT_FLAG_NAME, "", evaluationContext));
        assertEquals("fallback_str", client.getStringValue("non-existing", "fallback_str", evaluationContext));
    }

    @Test
    void getIntegerEvaluation() {
        mockFliptAPI("/evaluate/v1/variant", "variant-int.json", INT_FLAG_NAME);
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        evaluationContext.add("userId", "int");
        assertEquals(INT_FLAG_VALUE, fliptProvider.getIntegerEvaluation(INT_FLAG_NAME, 1,
                evaluationContext).getValue());
        assertEquals(INT_FLAG_VALUE, client.getIntegerValue(INT_FLAG_NAME, 1, evaluationContext));
        assertEquals(1, client.getIntegerValue("non-existing", 1, evaluationContext));

        // non-number flag value
        assertEquals(1, client.getIntegerValue(VARIANT_FLAG_NAME, 1, evaluationContext));
    }

    @Test
    void getDoubleEvaluation() {
        mockFliptAPI("/evaluate/v1/variant", "variant-double.json", DOUBLE_FLAG_NAME);
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        evaluationContext.add("userId", "double");
        assertEquals(DOUBLE_FLAG_VALUE, fliptProvider.getDoubleEvaluation(DOUBLE_FLAG_NAME, 1.1,
                evaluationContext).getValue());
        assertEquals(DOUBLE_FLAG_VALUE, client.getDoubleValue(DOUBLE_FLAG_NAME, 1.1, evaluationContext));
        assertEquals(1.1, client.getDoubleValue("non-existing", 1.1, evaluationContext));

        // non-number flag value
        assertEquals(1.1, client.getDoubleValue(VARIANT_FLAG_NAME, 1.1, evaluationContext));
    }

    @Test
    void getStringEvaluationByUser() {
        mockFliptAPI("/evaluate/v1/variant", "variant.json", "111");
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        evaluationContext.add("userId", "111");
        assertEquals(VARIANT_FLAG_VALUE,
                fliptProvider.getStringEvaluation(USERS_FLAG_NAME, "", evaluationContext).getValue());
        assertEquals(VARIANT_FLAG_VALUE, client.getStringValue(USERS_FLAG_NAME, "", evaluationContext));
        evaluationContext.add("userId", "2");
        assertEquals("", client.getStringValue(USERS_FLAG_NAME, "", evaluationContext));
    }

    @Test
    void getEvaluationMetadataTest() {
        mockFliptAPI("/evaluate/v1/variant", "variant.json", VARIANT_FLAG_NAME);
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        ProviderEvaluation<String> stringEvaluation = fliptProvider.getStringEvaluation(VARIANT_FLAG_NAME, "",
                evaluationContext);
        ImmutableMetadata flagMetadata = stringEvaluation.getFlagMetadata();
        assertEquals("attachment-1", flagMetadata.getString("variant-attachment"));
        FlagEvaluationDetails<String> nonExistingFlagEvaluation = client.getStringDetails("non-existing", "",
                evaluationContext);
        assertNull(nonExistingFlagEvaluation.getFlagMetadata().getBoolean("variant-attachment"));
    }

    @SneakyThrows
    @Test
    void getObjectEvaluationTest() {
        mockFliptAPI("/evaluate/v1/variant", "variant-object.json", OBJECT_FLAG_NAME);
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        evaluationContext.add("userId", "object");

        Value expectedValue = new Value("{\"key1\":\"value1\",\"key2\":42,\"key3\":true}");
        Value emptyValue = new Value();

        assertEquals(expectedValue, client.getObjectValue(OBJECT_FLAG_NAME, emptyValue, evaluationContext));
        assertEquals(emptyValue, client.getObjectValue("non-existing", emptyValue, evaluationContext));

        // non-object flag value
        assertEquals(emptyValue, client.getObjectValue(VARIANT_FLAG_NAME, emptyValue, evaluationContext));
    }
}