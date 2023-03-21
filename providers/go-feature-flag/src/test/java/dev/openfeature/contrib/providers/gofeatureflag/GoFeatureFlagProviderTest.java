package dev.openfeature.contrib.providers.gofeatureflag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidTargetingKey;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.jupiter.api.Assertions.*;

class GoFeatureFlagProviderTest {
    // Dispatcher is the configuration of the mock server to test the provider.
    final Dispatcher dispatcher = new Dispatcher() {
        @SneakyThrows
        @Override
        public MockResponse dispatch(RecordedRequest request) {
            if (request.getPath().contains("fail_500")) {
                return new MockResponse().setResponseCode(500);
            }
            if (request.getPath().startsWith("/v1/feature/")) {
                String flagName = request.getPath().replace("/v1/feature/", "").replace("/eval", "");
                return new MockResponse()
                        .setResponseCode(200)
                        .setBody(readMockResponse(flagName + ".json"));
            }
            return new MockResponse().setResponseCode(404);
        }
    };
    private MockWebServer server;
    private HttpUrl baseUrl;
    private MutableContext evaluationContext;

    @BeforeEach
    void beforeEach() throws IOException {
        this.server = new MockWebServer();
        this.server.setDispatcher(dispatcher);
        this.server.start();
        this.baseUrl = server.url("");

        this.evaluationContext = new MutableContext();
        this.evaluationContext.setTargetingKey("d45e303a-38c2-11ed-a261-0242ac120002");
        this.evaluationContext.add("email", "john.doe@gofeatureflag.org");
        this.evaluationContext.add("firstname", "john");
        this.evaluationContext.add("lastname", "doe");
        this.evaluationContext.add("anonymous", false);
        this.evaluationContext.add("professional", true);
        this.evaluationContext.add("rate", 3.14);
        this.evaluationContext.add("age", 30);
        this.evaluationContext.add("company_info", new MutableStructure().add("name", "my_company").add("size", 120));
        List<Value> labels = new ArrayList<>();
        labels.add(new Value("pro"));
        labels.add(new Value("beta"));
        this.evaluationContext.add("labels", labels);
    }

    @AfterEach
    void afterEach() throws IOException {
        this.server.close();
        this.server = null;
        this.baseUrl = null;
    }

    @Test
    void getMetadata_validate_name() throws InvalidOptions {
        assertEquals("GO Feature Flag Provider", new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build()).getMetadata().getName());
    }

    @Test
    void constructor_options_null() {
        assertThrows(InvalidOptions.class, () -> new GoFeatureFlagProvider(null));
    }

    @Test
    void constructor_options_empty() {
        assertThrows(InvalidOptions.class, () -> new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().build()));
    }

    @Test
    void constructor_options_empty_endpoint() {
        assertThrows(InvalidEndpoint.class, () -> new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint("").build()));
    }

    @Test
    void constructor_options_only_timeout() {
        assertThrows(InvalidEndpoint.class, () -> new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().timeout(10000).build()));
    }

    @Test
    void constructor_options_valid_endpoint() {
        assertDoesNotThrow(() -> new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint("http://localhost:1031").build()));
    }

    @Test
    void should_throw_an_error_if_endpoint_not_available() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        assertThrows(GeneralError.class, () -> g.getBooleanEvaluation("fail_500", false, this.evaluationContext));
    }

    @Test
    void should_throw_an_error_if_flag_does_not_exists() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        assertThrows(FlagNotFoundError.class, () -> g.getBooleanEvaluation("flag_not_found", false, this.evaluationContext));
    }

    @Test
    void should_throw_an_error_if_we_expect_a_boolean_and_got_another_type() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        assertThrows(TypeMismatchError.class, () -> g.getBooleanEvaluation("string_key", false, this.evaluationContext));
    }

    @Test
    void should_resolve_a_valid_boolean_flag_with_TARGETING_MATCH_reason() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        ProviderEvaluation<Boolean> res = g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext);
        assertEquals(true, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
    }

    @Test
    void should_return_custom_reason_if_returned_by_relay_proxy() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        ProviderEvaluation<Boolean> res = g.getBooleanEvaluation("unknown_reason", false, this.evaluationContext);
        assertEquals(true, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals("CUSTOM_REASON", res.getReason());
        assertEquals("True", res.getVariant());
    }

    @Test
    void should_use_boolean_default_value_if_the_flag_is_disabled() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        ProviderEvaluation<Boolean> res = g.getBooleanEvaluation("disabled", false, this.evaluationContext);
        assertEquals(false, res.getValue());
        assertEquals(Reason.DISABLED.toString(), res.getReason());
    }

    @Test
    void should_throw_an_error_if_we_expect_a_string_and_got_another_type() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        assertThrows(TypeMismatchError.class, () -> g.getStringEvaluation("bool_targeting_match", "defaultValue", this.evaluationContext));
    }

    @Test
    void should_resolve_a_valid_string_flag_with_TARGETING_MATCH_reason() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        ProviderEvaluation<String> res = g.getStringEvaluation("string_key", "defaultValue", this.evaluationContext);
        assertEquals("CC0000", res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
    }

    @Test
    void should_use_string_default_value_if_the_flag_is_disabled() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        ProviderEvaluation<String> res = g.getStringEvaluation("disabled", "defaultValue", this.evaluationContext);
        assertEquals("defaultValue", res.getValue());
        assertEquals(Reason.DISABLED.toString(), res.getReason());
    }

    @Test
    void should_throw_an_error_if_we_expect_a_integer_and_got_another_type() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        assertThrows(TypeMismatchError.class, () -> g.getIntegerEvaluation("string_key", 200, this.evaluationContext));
    }

    @Test
    void should_resolve_a_valid_integer_flag_with_TARGETING_MATCH_reason() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        ProviderEvaluation<Integer> res = g.getIntegerEvaluation("integer_key", 1200, this.evaluationContext);
        assertEquals(100, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
    }

    @Test
    void should_use_integer_default_value_if_the_flag_is_disabled() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        ProviderEvaluation<Integer> res = g.getIntegerEvaluation("disabled", 1225, this.evaluationContext);
        assertEquals(1225, res.getValue());
        assertEquals(Reason.DISABLED.toString(), res.getReason());
    }

    @Test
    void should_throw_an_error_if_we_expect_a_integer_and_double_type() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        assertThrows(TypeMismatchError.class, () -> g.getIntegerEvaluation("double_key", 200, this.evaluationContext));
    }

    @Test
    void should_resolve_a_valid_double_flag_with_TARGETING_MATCH_reason() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        ProviderEvaluation<Double> res = g.getDoubleEvaluation("double_key", 1200.25, this.evaluationContext);
        assertEquals(100.25, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
    }

    @Test
    void should_use_double_default_value_if_the_flag_is_disabled() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        ProviderEvaluation<Double> res = g.getDoubleEvaluation("disabled", 1225.34, this.evaluationContext);
        assertEquals(1225.34, res.getValue());
        assertEquals(Reason.DISABLED.toString(), res.getReason());
    }

    @Test
    void should_resolve_a_valid_value_flag_with_TARGETING_MATCH_reason() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        ProviderEvaluation<Value> res = g.getObjectEvaluation("object_key", null, this.evaluationContext);
        Value want = new Value(new MutableStructure().add("test", "test1").add("test2", false).add("test3", 123.3).add("test4", 1));
        assertEquals(want, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
    }

    @Test
    void should_wrap_into_value_if_wrong_type() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        ProviderEvaluation<Value> res = g.getObjectEvaluation("string_key", null, this.evaluationContext);
        Value want = new Value("CC0000");
        assertEquals(want, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
    }

    @Test
    void should_use_object_default_value_if_the_flag_is_disabled() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        ProviderEvaluation<Value> res = g.getObjectEvaluation("disabled", new Value("default"), this.evaluationContext);
        assertEquals(new Value("default"), res.getValue());
        assertEquals(Reason.DISABLED.toString(), res.getReason());
    }


    @Test
    void should_resolve_a_valid_value_flag_with_a_list() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        assertThrows(InvalidTargetingKey.class, () -> g.getObjectEvaluation("list_key", null, new MutableContext()));
    }

    @Test
    void should_throw_an_error_if_no_targeting_key() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        ProviderEvaluation<Value> res = g.getObjectEvaluation("list_key", null, this.evaluationContext);
        Value want = new Value(new ArrayList<>(
                Arrays.asList(new Value("test"),
                              new Value("test1"),
                              new Value("test2"),
                              new Value("false"),
                              new Value("test3"))));
        assertEquals(want, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
    }

    @Test
    void should_not_fail_if_receive_an_unknown_field_in_response() throws InvalidOptions {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        ProviderEvaluation<Boolean> res = g.getBooleanEvaluation("unknown_field", false, this.evaluationContext);
        assertEquals(true, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
    }

    private String readMockResponse(String filename) throws IOException {
        String file = getClass().getClassLoader().getResource("mock_responses/" + filename).getFile();
        byte[] bytes = Files.readAllBytes(Paths.get(file));
        return new String(bytes);
    }
}
