package dev.openfeature.contrib.providers.v2.gofeatureflag.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.contrib.providers.v2.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.v2.gofeatureflag.TestUtils;
import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.v2.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.v2.gofeatureflag.util.Const;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.InvalidContextError;
import java.io.IOException;
import java.util.HashMap;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@Slf4j
public class GoFeatureFlagApiTest {
    private MockWebServer server;
    private HttpUrl baseUrl;
    private MutableContext evaluationContext;

    @BeforeEach
    void beforeEach(TestInfo testInfo) throws IOException {
        this.server = new MockWebServer();
        val goffAPIMock = new GoffApiMock();
        this.server.setDispatcher(goffAPIMock.dispatcher);
        this.server.start();
        this.baseUrl = server.url("");
    }

    @AfterEach
    void afterEach() throws IOException {
        this.server.close();
        this.server = null;
        this.baseUrl = null;
    }

    @SneakyThrows
    @Test
    public void ShouldThrowInvalidOptionsIfEndpointMissing() {
        val options = GoFeatureFlagProviderOptions.builder()
                .build();
        assertThrows(InvalidEndpoint.class, () -> GoFeatureFlagApi.builder().options(options).build());
    }

    @SneakyThrows
    @Test
    public void ShouldThrowInvalidOptionsIfEndpointEmpty() {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint("")
                .build();
        assertThrows(InvalidEndpoint.class, () -> GoFeatureFlagApi.builder().options(options).build());
    }

    @SneakyThrows
    @Test
    public void ShouldThrowInvalidOptionsIfEndpointInvalid() {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint("ccccc")
                .build();
        assertThrows(InvalidEndpoint.class, () -> GoFeatureFlagApi.builder().options(options).build());
    }


    /**
     * Test Evaluate Method
     */
    @SneakyThrows
    @Test
    public void RequestShouldCallTheOfrepEndpoint() {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        api.evaluateFlag("flag-key", TestUtils.defaultEvaluationContext);

        val want = "/ofrep/v1/evaluate/flags/flag-key";
        assertEquals(want, server.takeRequest().getPath());
    }

    @SneakyThrows
    @Test
    public void RequestShouldHaveAnAPIKey() {
        val apiKey = "my-api-key";
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .apiKey(apiKey)
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        api.evaluateFlag("flag-key", TestUtils.defaultEvaluationContext);

        val want = Const.BEARER_TOKEN + apiKey;
        assertEquals(want, server.takeRequest().getHeader("Authorization"));
    }

    @SneakyThrows
    @Test
    public void RequestShouldNotSetAnAPIKeyIfEmpty() {
        val apiKey = "";
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .apiKey(apiKey)
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        api.evaluateFlag("flag-key", TestUtils.defaultEvaluationContext);
        assertNull(server.takeRequest().getHeader("Authorization"));
    }

    @SneakyThrows
    @Test
    public void RequestShouldHaveTheEvaluationContextInTheBody() {
        val apiKey = "my-api-key";
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .apiKey(apiKey)
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        api.evaluateFlag("flag-key", TestUtils.defaultEvaluationContext);

        val wantStr = "{\"context\":{"
                + "  \"targetingKey\": \"d45e303a-38c2-11ed-a261-0242ac120002\","
                + "  \"email\": \"john.doe@gofeatureflag.org\","
                + "  \"firstname\": \"john\","
                + "  \"lastname\": \"doe\","
                + "  \"anonymous\": false,"
                + "  \"professional\": true,"
                + "  \"rate\": 3.14,"
                + "  \"age\": 30,"
                + "  \"company_info\": {\"name\": \"my_company\", \"size\": 120},"
                + "  \"labels\": [\"pro\", \"beta\"]"
                + "}}";
        val gotStr = server.takeRequest().getBody().readUtf8();
        ObjectMapper objectMapper = new ObjectMapper();
        Object want = objectMapper.readTree(wantStr);
        Object got = objectMapper.readTree(gotStr);
        assertEquals(want, got, "The JSON strings are not equal");
    }

    @SneakyThrows
    @Test
    public void RequestShouldHaveDefaultHeaders() {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        api.evaluateFlag("flag-key", TestUtils.defaultEvaluationContext);

        val got = server.takeRequest().getHeaders();
        assertEquals("application/json; charset=utf-8", got.get(Const.HTTP_HEADER_CONTENT_TYPE));
    }

    @SneakyThrows
    @Test
    public void ShouldErrorIfTimeoutIsReached() {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(200)
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        assertThrows(GeneralError.class, () -> api.evaluateFlag("timeout", TestUtils.defaultEvaluationContext));
    }

    @SneakyThrows
    @Test
    public void ShouldErrorIfResponseIsA401() {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        assertThrows(GeneralError.class, () -> api.evaluateFlag("401", TestUtils.defaultEvaluationContext));
    }

    @SneakyThrows
    @Test
    public void ShouldErrorIfResponseIsA403() {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        assertThrows(GeneralError.class, () -> api.evaluateFlag("403", TestUtils.defaultEvaluationContext));
    }

    @SneakyThrows
    @Test
    public void ShouldErrorIfResponseHasInvalidJson() {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        assertThrows(GeneralError.class, () -> api.evaluateFlag("invalid-json", TestUtils.defaultEvaluationContext));
    }

    @SneakyThrows
    @Test
    public void ShouldErrorIfResponseIsA400() {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        assertThrows(InvalidContextError.class, () -> api.evaluateFlag("400", TestUtils.defaultEvaluationContext));
    }

    @SneakyThrows
    @Test
    public void ShouldErrorIfResponseIsA500() {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        assertThrows(GeneralError.class, () -> api.evaluateFlag("500", TestUtils.defaultEvaluationContext));
    }

    @SneakyThrows
    @Test
    public void ShouldHaveAValidEvaluateResponse() {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .build();
        val api = GoFeatureFlagApi.builder().options(options).build();
        val got = api.evaluateFlag("flag-key", TestUtils.defaultEvaluationContext);

        val want = new GoFeatureFlagResponse();
        want.setVariationType("off");
        want.setValue(false);
        want.setReason("STATIC");
        want.setCacheable(true);
        val metadata = new HashMap<String, Object>();
        metadata.put("description", "A flag that is always off");
        want.setMetadata(metadata);
        want.setErrorCode(null);
        want.setErrorDetails(null);
        want.setFailed(false);

        assertEquals(want, got);
    }
}
