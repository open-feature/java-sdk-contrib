package dev.openfeature.contrib.providers.gofeatureflag;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.cache.CacheBuilder;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import dev.openfeature.sdk.exceptions.TargetingKeyMissingError;
import org.jetbrains.annotations.NotNull;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import static dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProvider.CACHED_REASON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoFeatureFlagProviderTest {
    private int publishEventsRequestsReceived = 0;

    // Dispatcher is the configuration of the mock server to test the provider.
    final Dispatcher dispatcher = new Dispatcher() {
        @NotNull
        @SneakyThrows
        @Override
        public MockResponse dispatch(RecordedRequest request) {
            assert request.getPath() != null;
            if (request.getPath().contains("fail_500")) {
                return new MockResponse().setResponseCode(500);
            }
            if (request.getPath().contains("fail_401")) {
                return new MockResponse().setResponseCode(401);
            }
            if (request.getPath().startsWith("/v1/feature/")) {
                String flagName = request.getPath().replace("/v1/feature/", "").replace("/eval", "");
                return new MockResponse()
                    .setResponseCode(200)
                    .setBody(readMockResponse(flagName + ".json"));
            }
            if (request.getPath().startsWith("/v1/data/collector")) {
                MockResponse mockResponse;
                if (publishEventsRequestsReceived == 0) {

                    // simulate error on first attempt for retry
                    mockResponse = new MockResponse()
                        .setResponseCode(502);
                } else {
                    mockResponse = new MockResponse()
                        .setResponseCode(200);
                }
                publishEventsRequestsReceived++;
                return mockResponse;
            }
            return new MockResponse().setResponseCode(404);
        }
    };
    private MockWebServer server;
    private HttpUrl baseUrl;
    private MutableContext evaluationContext;

    private final static ImmutableMetadata defaultMetadata =
            ImmutableMetadata.builder()
                    .addString("pr_link", "https://github.com/thomaspoignant/go-feature-flag/pull/916")
                    .addInteger("version", 1)
                    .build();

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

    @SneakyThrows
    @Test
    void getMetadata_validate_name() {
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

    @SneakyThrows
    @Test
    void constructor_options_empty_endpoint() {
        assertThrows(InvalidEndpoint.class, () -> new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint("").build()));
    }

    @SneakyThrows
    @Test
    void constructor_options_only_timeout() {
        assertThrows(InvalidEndpoint.class, () -> new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().timeout(10000).build()));
    }

    @SneakyThrows
    @Test
    void constructor_options_valid_endpoint() {
        assertDoesNotThrow(() -> new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint("http://localhost:1031").build()));
    }

    @SneakyThrows
    @Test
    void should_return_not_ready_if_not_initialized() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build()){
            @Override
            public void initialize(EvaluationContext evaluationContext) throws Exception {

                // make the provider not initialized for this test
                Thread.sleep(3000);
            }
        };

        /*
         ErrorCode.PROVIDER_NOT_READY and default value should be returned when evaluated via the client,
         see next step in this test.
         */
        assertThrows(ProviderNotReadyError.class, ()-> g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext));

        String providerName = "shouldReturnNotReadyIfNotInitialized";
        OpenFeatureAPI.getInstance().setProvider(providerName, g);
        assertThat(OpenFeatureAPI.getInstance().getProvider(providerName).getState()).isEqualTo(ProviderState.NOT_READY);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> booleanFlagEvaluationDetails = client.getBooleanDetails("return_error_when_not_initialized", false, new ImmutableContext("targetingKey"));
        assertEquals(ErrorCode.PROVIDER_NOT_READY, booleanFlagEvaluationDetails.getErrorCode());
        assertEquals(Boolean.FALSE, booleanFlagEvaluationDetails.getValue());
    }

    @SneakyThrows
    @Test
    void client_test() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());

        String providerName = "clientTest";
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);

        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        Boolean value = client.getBooleanValue("bool_targeting_match",
        false);
        assertEquals(Boolean.FALSE, value, "should evaluate to default value without context");
        FlagEvaluationDetails<Boolean> booleanFlagEvaluationDetails = client.getBooleanDetails("bool_targeting_match",
        false, new ImmutableContext());
        assertEquals(Boolean.FALSE, booleanFlagEvaluationDetails.getValue(), "should evaluate to default value with empty context");
        assertEquals(ErrorCode.TARGETING_KEY_MISSING, booleanFlagEvaluationDetails.getErrorCode(), "should evaluate to default value with empty context");
        booleanFlagEvaluationDetails = client.getBooleanDetails("bool_targeting_match", false, new ImmutableContext("targetingKey"));
        assertEquals(Boolean.TRUE, booleanFlagEvaluationDetails.getValue(), "should evaluate with a valid context");
    }


    @SneakyThrows
    @Test
    void should_throw_an_error_if_endpoint_not_available() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        assertThrows(GeneralError.class, () -> g.getBooleanEvaluation("fail_500", false, this.evaluationContext));
    }

    @SneakyThrows
    @Test
    void should_throw_an_error_if_invalid_api_key() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(
                GoFeatureFlagProviderOptions.builder()
                        .endpoint(this.baseUrl.toString())
                        .timeout(1000)
                        .apiKey("invalid_api_key")
                        .build());
        g.initialize(new ImmutableContext());
        assertThrows(GeneralError.class, () -> g.getBooleanEvaluation("fail_401", false, this.evaluationContext));
    }

    @SneakyThrows
    @Test
    void should_throw_an_error_if_flag_does_not_exists() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        assertThrows(FlagNotFoundError.class, () -> g.getBooleanEvaluation("flag_not_found", false, this.evaluationContext));
    }

    @SneakyThrows
    @Test
    void should_throw_an_error_if_we_expect_a_boolean_and_got_another_type() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        assertThrows(TypeMismatchError.class, () -> g.getBooleanEvaluation("string_key", false, this.evaluationContext));
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_boolean_flag_with_TARGETING_MATCH_reason() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<Boolean> res = g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext);
        assertEquals(true, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
        assertAll("Test flagMetadata",
                () -> assertEquals(defaultMetadata.getInteger("version"), res.getFlagMetadata().getInteger("version")),
                () -> assertEquals(defaultMetadata.getString("pr_link"), res.getFlagMetadata().getString("pr_link")));
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_boolean_flag_with_TARGETING_MATCH_reason_cache_disabled() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
            .endpoint(this.baseUrl.toString())
            .timeout(1000)
            .enableCache(false)
            .build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<Boolean> res = g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext);
        assertEquals(true, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());

        res = g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext);
        assertEquals(true, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
        g.shutdown();
    }

    @SneakyThrows
    @Test
    void should_resolve_from_cache() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<Boolean> res = g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext);
        assertEquals(true, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());

        res = g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext);
        assertEquals(true, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(CACHED_REASON, res.getReason());
        assertEquals("True", res.getVariant());
    }

    @SneakyThrows
    @Test
    void should_resolve_from_cache_max_size() {
        CacheBuilder cacheBuilder = CacheBuilder.newBuilder().maximumSize(1);
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).cacheBuilder(cacheBuilder).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<Boolean> res = g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext);
        assertEquals(true, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());

        res = g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext);
        assertEquals(true, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(CACHED_REASON, res.getReason());
        assertEquals("True", res.getVariant());

        ProviderEvaluation<String> strRes = g.getStringEvaluation("string_key", "defaultValue", this.evaluationContext);
        assertEquals("CC0000", strRes.getValue());
        assertNull(strRes.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), strRes.getReason());
        assertEquals("True", strRes.getVariant());

        strRes = g.getStringEvaluation("string_key", "defaultValue", this.evaluationContext);
        assertEquals("CC0000", strRes.getValue());
        assertNull(strRes.getErrorCode());
        assertEquals(CACHED_REASON, strRes.getReason());
        assertEquals("True", strRes.getVariant());

        // verify that value previously fetch from cache now not fetched from cache since cache max size is 1, and cache is full.
        res = g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext);
        assertEquals(true, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
    }

    @SneakyThrows
    @Test
    void should_return_custom_reason_if_returned_by_relay_proxy() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<Boolean> res = g.getBooleanEvaluation("unknown_reason", false, this.evaluationContext);
        assertEquals(true, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals("CUSTOM_REASON", res.getReason());
        assertEquals("True", res.getVariant());
        assertAll("Test flagMetadata",
                () -> assertEquals(defaultMetadata.getInteger("version"), res.getFlagMetadata().getInteger("version")),
                () -> assertEquals(defaultMetadata.getString("pr_link"), res.getFlagMetadata().getString("pr_link")));
    }

    @SneakyThrows
    @Test
    void should_use_boolean_default_value_if_the_flag_is_disabled() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<Boolean> res = g.getBooleanEvaluation("disabled", false, this.evaluationContext);
        assertEquals(false, res.getValue());
        assertEquals(Reason.DISABLED.toString(), res.getReason());
    }

    @SneakyThrows
    @Test
    void should_throw_an_error_if_we_expect_a_string_and_got_another_type() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        assertThrows(TypeMismatchError.class, () -> g.getStringEvaluation("bool_targeting_match", "defaultValue", this.evaluationContext));
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_string_flag_with_TARGETING_MATCH_reason() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<String> res = g.getStringEvaluation("string_key", "defaultValue", this.evaluationContext);
        assertEquals("CC0000", res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
        assertAll("Test flagMetadata",
                () -> assertEquals(defaultMetadata.getInteger("version"), res.getFlagMetadata().getInteger("version")),
                () -> assertEquals(defaultMetadata.getString("pr_link"), res.getFlagMetadata().getString("pr_link")));
    }

    @SneakyThrows
    @Test
    void should_use_string_default_value_if_the_flag_is_disabled() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<String> res = g.getStringEvaluation("disabled", "defaultValue", this.evaluationContext);
        assertEquals("defaultValue", res.getValue());
        assertEquals(Reason.DISABLED.toString(), res.getReason());
    }

    @SneakyThrows
    @Test
    void should_throw_an_error_if_we_expect_a_integer_and_got_another_type() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        assertThrows(TypeMismatchError.class, () -> g.getIntegerEvaluation("string_key", 200, this.evaluationContext));
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_integer_flag_with_TARGETING_MATCH_reason() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<Integer> res = g.getIntegerEvaluation("integer_key", 1200, this.evaluationContext);
        assertEquals(100, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
        assertAll("Test flagMetadata",
                () -> assertEquals(defaultMetadata.getInteger("version"), res.getFlagMetadata().getInteger("version")),
                () -> assertEquals(defaultMetadata.getString("pr_link"), res.getFlagMetadata().getString("pr_link")));
    }

    @SneakyThrows
    @Test
    void should_use_integer_default_value_if_the_flag_is_disabled() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<Integer> res = g.getIntegerEvaluation("disabled", 1225, this.evaluationContext);
        assertEquals(1225, res.getValue());
        assertEquals(Reason.DISABLED.toString(), res.getReason());
    }

    @SneakyThrows
    @Test
    void should_throw_an_error_if_we_expect_a_integer_and_double_type() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        assertThrows(TypeMismatchError.class, () -> g.getIntegerEvaluation("double_key", 200, this.evaluationContext));
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_double_flag_with_TARGETING_MATCH_reason() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<Double> res = g.getDoubleEvaluation("double_key", 1200.25, this.evaluationContext);
        assertEquals(100.25, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
        assertAll("Test flagMetadata",
                () -> assertEquals(defaultMetadata.getInteger("version"), res.getFlagMetadata().getInteger("version")),
                () -> assertEquals(defaultMetadata.getString("pr_link"), res.getFlagMetadata().getString("pr_link")));
    }

    @SneakyThrows
    @Test
    void should_use_double_default_value_if_the_flag_is_disabled() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<Double> res = g.getDoubleEvaluation("disabled", 1225.34, this.evaluationContext);
        assertEquals(1225.34, res.getValue());
        assertEquals(Reason.DISABLED.toString(), res.getReason());
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_value_flag_with_TARGETING_MATCH_reason() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<Value> res = g.getObjectEvaluation("object_key", null, this.evaluationContext);
        Value want = new Value(new MutableStructure().add("test", "test1").add("test2", false).add("test3", 123.3).add("test4", 1));
        assertEquals(want, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
        assertAll("Test flagMetadata",
                () -> assertEquals(defaultMetadata.getInteger("version"), res.getFlagMetadata().getInteger("version")),
                () -> assertEquals(defaultMetadata.getString("pr_link"), res.getFlagMetadata().getString("pr_link")));
    }

    @SneakyThrows
    @Test
    void should_wrap_into_value_if_wrong_type() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<Value> res = g.getObjectEvaluation("string_key", null, this.evaluationContext);
        Value want = new Value("CC0000");
        assertEquals(want, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
        assertAll("Test flagMetadata",
                () -> assertEquals(defaultMetadata.getInteger("version"), res.getFlagMetadata().getInteger("version")),
                () -> assertEquals(defaultMetadata.getString("pr_link"), res.getFlagMetadata().getString("pr_link")));
    }

    @SneakyThrows
    @Test
    void should_use_object_default_value_if_the_flag_is_disabled() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<Value> res = g.getObjectEvaluation("disabled", new Value("default"), this.evaluationContext);
        assertEquals(new Value("default"), res.getValue());
        assertEquals(Reason.DISABLED.toString(), res.getReason());
    }


    @SneakyThrows
    @Test
    void should_throw_an_error_if_no_targeting_key() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        assertThrows(TargetingKeyMissingError.class, () -> g.getObjectEvaluation("list_key", null, new MutableContext()));
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_value_flag_with_a_list() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
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
        assertAll("Test flagMetadata",
                () -> assertEquals(defaultMetadata.getInteger("version"), res.getFlagMetadata().getInteger("version")),
                () -> assertEquals(defaultMetadata.getString("pr_link"), res.getFlagMetadata().getString("pr_link")));
    }

    @SneakyThrows
    @Test
    void should_not_fail_if_receive_an_unknown_field_in_response() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        ProviderEvaluation<Boolean> res = g.getBooleanEvaluation("unknown_field", false, this.evaluationContext);
        assertEquals(true, res.getValue());
        assertNull(res.getErrorCode());
        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
        assertEquals("True", res.getVariant());
        assertAll("Test flagMetadata",
                () -> assertEquals(defaultMetadata.getInteger("version"), res.getFlagMetadata().getInteger("version")),
                () -> assertEquals(defaultMetadata.getString("pr_link"), res.getFlagMetadata().getString("pr_link")));
    }

    @SneakyThrows
    @Test
    void should_publish_events() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder().endpoint(this.baseUrl.toString()).timeout(1000).build());
        g.initialize(new ImmutableContext());
        g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext);
        g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext);
        g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext);
        assertEquals(0, g.getEventsPublisher().publish(), "first attempt expected to fail");

        // simulate publish on next interval
        assertEquals(2, g.getEventsPublisher().publish(), "expected to publish all events after retry");

        g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext);
        g.getBooleanEvaluation("bool_targeting_match", false, this.evaluationContext);
        assertEquals(2, g.getEventsPublisher().publish());

        assertEquals(0, g.getEventsPublisher().publish());
        g.shutdown();
    }

    private String readMockResponse(String filename) throws Exception {
        URL url = getClass().getClassLoader().getResource("mock_responses/" + filename);
        assert url != null;
        byte[] bytes = Files.readAllBytes(Paths.get(url.toURI()));
        return new String(bytes);
    }
}
