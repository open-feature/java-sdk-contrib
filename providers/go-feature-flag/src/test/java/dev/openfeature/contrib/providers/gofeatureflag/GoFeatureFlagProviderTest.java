package dev.openfeature.contrib.providers.gofeatureflag;

import static dev.openfeature.contrib.providers.gofeatureflag.controller.GoFeatureFlagController.requestMapper;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.net.HttpHeaders;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@Slf4j
class GoFeatureFlagProviderTest {
    private int publishEventsRequestsReceived = 0;
    private Map exporterMetadata;
    private int flagChangeCallCounter = 0;
    private boolean flagChanged404 = false;
    private List<RecordedRequest> requests = new ArrayList<>();

    // Dispatcher is the configuration of the mock server to test the provider.
    final Dispatcher dispatcher = new Dispatcher() {
        @NotNull @SneakyThrows
        @Override
        public MockResponse dispatch(RecordedRequest request) {
            requests.add(request);
            assert request.getPath() != null;
            if (request.getPath().contains("fail_500")) {
                return new MockResponse().setResponseCode(500);
            }
            if (request.getPath().contains("fail_401")) {
                return new MockResponse().setResponseCode(401);
            }
            if (request.getPath().startsWith("/v1/feature/")) {
                String flagName = request.getPath().replace("/v1/feature/", "").replace("/eval", "");
                return new MockResponse().setResponseCode(200).setBody(readMockResponse(flagName + ".json"));
            }
            if (request.getPath().startsWith("/v1/data/collector")) {
                String requestBody = request.getBody().readString(StandardCharsets.UTF_8);
                Map<String, Object> map = requestMapper.readValue(requestBody, Map.class);
                publishEventsRequestsReceived = ((List) map.get("events")).size();
                exporterMetadata = ((Map) map.get("meta"));
                if (requestBody.contains("fail_500") && publishEventsRequestsReceived == 1) {
                    return new MockResponse().setResponseCode(502);
                }
                return new MockResponse().setResponseCode(200);
            }
            if (request.getPath().contains("/v1/flag/change")) {
                flagChangeCallCounter++;
                if (flagChanged404) {
                    return new MockResponse().setResponseCode(404);
                }
                if (flagChangeCallCounter == 2) {
                    return new MockResponse().setResponseCode(200).setHeader(HttpHeaders.ETAG, "7891011");
                }
                if (request.getHeader(HttpHeaders.IF_NONE_MATCH) != null
                        && (request.getHeader(HttpHeaders.IF_NONE_MATCH).equals("123456")
                                || request.getHeader(HttpHeaders.IF_NONE_MATCH).equals("7891011"))) {
                    return new MockResponse().setResponseCode(304);
                }

                return new MockResponse().setResponseCode(200).setHeader(HttpHeaders.ETAG, "123456");
            }
            return new MockResponse().setResponseCode(404);
        }
    };
    private MockWebServer server;
    private HttpUrl baseUrl;
    private MutableContext evaluationContext;

    private static final ImmutableMetadata defaultMetadata = ImmutableMetadata.builder()
            .addString("pr_link", "https://github.com/thomaspoignant/go-feature-flag/pull/916")
            .addInteger("version", 1)
            .build();

    private String testName;

    @BeforeEach
    void beforeEach(TestInfo testInfo) throws IOException {
        this.flagChangeCallCounter = 0;
        this.flagChanged404 = false;
        this.testName = testInfo.getDisplayName();
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
        this.evaluationContext.add(
                "company_info", new MutableStructure().add("name", "my_company").add("size", 120));
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
        OpenFeatureAPI.getInstance().shutdown();
    }

    @SneakyThrows
    @Test
    void getMetadata_validate_name() {
        assertEquals(
                "GO Feature Flag Provider",
                new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                                .endpoint(this.baseUrl.toString())
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

    @SneakyThrows
    @Test
    void client_test() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = "clientTest";
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        Boolean value = client.getBooleanValue("bool_targeting_match", false);
        assertEquals(Boolean.FALSE, value, "should evaluate to default value without context");
        FlagEvaluationDetails<Boolean> booleanFlagEvaluationDetails =
                client.getBooleanDetails("bool_targeting_match", false, new ImmutableContext());
        assertEquals(
                Boolean.FALSE,
                booleanFlagEvaluationDetails.getValue(),
                "should evaluate to default value with empty context");
        assertEquals(
                ErrorCode.TARGETING_KEY_MISSING,
                booleanFlagEvaluationDetails.getErrorCode(),
                "should evaluate to default value with empty context");
        booleanFlagEvaluationDetails =
                client.getBooleanDetails("bool_targeting_match", false, new ImmutableContext("targetingKey"));
        assertEquals(Boolean.TRUE, booleanFlagEvaluationDetails.getValue(), "should evaluate with a valid context");
    }

    @SneakyThrows
    @Test
    void should_throw_an_error_if_endpoint_not_available() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> got = client.getBooleanDetails("fail_500", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want = FlagEvaluationDetails.<Boolean>builder()
                .value(false)
                .reason(Reason.ERROR.name())
                .errorCode(ErrorCode.GENERAL)
                .errorMessage("unknown error while retrieving flag fail_500")
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_throw_an_error_if_invalid_api_key() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .apiKey("invalid_api_key")
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> got = client.getBooleanDetails("fail_401", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want = FlagEvaluationDetails.<Boolean>builder()
                .value(false)
                .reason(Reason.ERROR.name())
                .errorCode(ErrorCode.GENERAL)
                .errorMessage("authentication/authorization error")
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_throw_an_error_if_flag_does_not_exists() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> got = client.getBooleanDetails("flag_not_found", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want = FlagEvaluationDetails.<Boolean>builder()
                .value(false)
                .reason(Reason.ERROR.name())
                .errorCode(ErrorCode.FLAG_NOT_FOUND)
                .errorMessage("Flag flag_not_found was not found in your configuration")
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_throw_an_error_if_we_expect_a_boolean_and_got_another_type() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> got = client.getBooleanDetails("string_key", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want = FlagEvaluationDetails.<Boolean>builder()
                .value(false)
                .reason(Reason.ERROR.name())
                .errorCode(ErrorCode.TYPE_MISMATCH)
                .errorMessage(
                        "Flag value string_key had unexpected type class java.lang.String, expected class java.lang.Boolean.")
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_boolean_flag_with_TARGETING_MATCH_reason() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> got =
                client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want = FlagEvaluationDetails.<Boolean>builder()
                .value(true)
                .variant("True")
                .flagKey("bool_targeting_match")
                .reason(Reason.TARGETING_MATCH.name())
                .flagMetadata(defaultMetadata)
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_boolean_flag_with_TARGETING_MATCH_reason_without_error_code_in_payload() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> got =
                client.getBooleanDetails("bool_targeting_match_no_error_code", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want = FlagEvaluationDetails.<Boolean>builder()
                .value(true)
                .variant("True")
                .flagKey("bool_targeting_match_no_error_code")
                .reason(Reason.TARGETING_MATCH.name())
                .flagMetadata(defaultMetadata)
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_boolean_flag_with_TARGETING_MATCH_reason_cache_disabled() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .enableCache(false)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> got =
                client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want = FlagEvaluationDetails.<Boolean>builder()
                .value(true)
                .variant("True")
                .flagKey("bool_targeting_match")
                .reason(Reason.TARGETING_MATCH.name())
                .flagMetadata(defaultMetadata)
                .build();
        assertEquals(want, got);
        got = client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_resolve_from_cache() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> got =
                client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want = FlagEvaluationDetails.<Boolean>builder()
                .value(true)
                .variant("True")
                .flagKey("bool_targeting_match")
                .reason(Reason.TARGETING_MATCH.name())
                .flagMetadata(defaultMetadata)
                .build();
        assertEquals(want, got);
        got = client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want2 = FlagEvaluationDetails.<Boolean>builder()
                .value(true)
                .variant("True")
                .flagKey("bool_targeting_match")
                .reason(Reason.CACHED.name())
                .flagMetadata(defaultMetadata)
                .build();
        assertEquals(want2, got);
    }

    @SneakyThrows
    @Test
    void should_resolve_from_cache_max_size() {
        Caffeine caffeine = Caffeine.newBuilder().maximumSize(1);
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .cacheConfig(caffeine)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> got =
                client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want = FlagEvaluationDetails.<Boolean>builder()
                .value(true)
                .variant("True")
                .flagKey("bool_targeting_match")
                .reason(Reason.TARGETING_MATCH.name())
                .flagMetadata(defaultMetadata)
                .build();
        assertEquals(want, got);

        got = client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want2 = FlagEvaluationDetails.<Boolean>builder()
                .value(true)
                .variant("True")
                .flagKey("bool_targeting_match")
                .reason(Reason.CACHED.name())
                .flagMetadata(defaultMetadata)
                .build();
        assertEquals(want2, got);

        FlagEvaluationDetails<String> gotStr =
                client.getStringDetails("string_key", "defaultValue", this.evaluationContext);
        FlagEvaluationDetails<String> wantStr = FlagEvaluationDetails.<String>builder()
                .value("CC0000")
                .variant("True")
                .flagKey("string_key")
                .reason(Reason.TARGETING_MATCH.name())
                .flagMetadata(defaultMetadata)
                .build();
        assertEquals(wantStr, gotStr);

        gotStr = client.getStringDetails("string_key", "defaultValue", this.evaluationContext);
        FlagEvaluationDetails<String> wantStr2 = FlagEvaluationDetails.<String>builder()
                .value("CC0000")
                .variant("True")
                .flagKey("string_key")
                .reason(Reason.CACHED.name())
                .flagMetadata(defaultMetadata)
                .build();
        assertEquals(wantStr2, gotStr);
    }

    @SneakyThrows
    @Test
    void should_return_custom_reason_if_returned_by_relay_proxy() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> got = client.getBooleanDetails("unknown_reason", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want = FlagEvaluationDetails.<Boolean>builder()
                .value(true)
                .variant("True")
                .flagKey("unknown_reason")
                .reason("CUSTOM_REASON")
                .flagMetadata(defaultMetadata)
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_use_boolean_default_value_if_the_flag_is_disabled() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> got = client.getBooleanDetails("disabled", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want = FlagEvaluationDetails.<Boolean>builder()
                .value(false)
                .variant("defaultSdk")
                .flagKey("disabled")
                .reason(Reason.DISABLED.name())
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_throw_an_error_if_we_expect_a_string_and_got_another_type() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<String> got =
                client.getStringDetails("bool_targeting_match", "defaultValue", this.evaluationContext);
        FlagEvaluationDetails<String> want = FlagEvaluationDetails.<String>builder()
                .value("defaultValue")
                .reason(Reason.ERROR.name())
                .errorMessage(
                        "Flag value bool_targeting_match had unexpected type class java.lang.Boolean, expected class java.lang.String.")
                .errorCode(ErrorCode.TYPE_MISMATCH)
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_string_flag_with_TARGETING_MATCH_reason() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<String> got =
                client.getStringDetails("string_key", "defaultValue", this.evaluationContext);
        FlagEvaluationDetails<String> want = FlagEvaluationDetails.<String>builder()
                .value("CC0000")
                .flagKey("string_key")
                .flagMetadata(defaultMetadata)
                .variant("True")
                .reason(Reason.TARGETING_MATCH.name())
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_use_string_default_value_if_the_flag_is_disabled() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<String> got = client.getStringDetails("disabled", "defaultValue", this.evaluationContext);
        FlagEvaluationDetails<String> want = FlagEvaluationDetails.<String>builder()
                .value("defaultValue")
                .variant("defaultSdk")
                .flagKey("disabled")
                .reason(Reason.DISABLED.name())
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_throw_an_error_if_we_expect_a_integer_and_got_another_type() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Integer> got =
                client.getIntegerDetails("bool_targeting_match", 200, this.evaluationContext);
        FlagEvaluationDetails<Integer> want = FlagEvaluationDetails.<Integer>builder()
                .value(200)
                .reason(Reason.ERROR.name())
                .errorMessage(
                        "Flag value bool_targeting_match had unexpected type class java.lang.Boolean, expected class java.lang.Integer.")
                .errorCode(ErrorCode.TYPE_MISMATCH)
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_integer_flag_with_TARGETING_MATCH_reason() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Integer> got = client.getIntegerDetails("integer_key", 200, this.evaluationContext);
        FlagEvaluationDetails<Integer> want = FlagEvaluationDetails.<Integer>builder()
                .value(100)
                .reason(Reason.TARGETING_MATCH.name())
                .variant("True")
                .flagMetadata(defaultMetadata)
                .flagKey("integer_key")
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_use_integer_default_value_if_the_flag_is_disabled() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Integer> got = client.getIntegerDetails("disabled", 200, this.evaluationContext);
        FlagEvaluationDetails<Integer> want = FlagEvaluationDetails.<Integer>builder()
                .value(200)
                .variant("defaultSdk")
                .flagKey("disabled")
                .reason(Reason.DISABLED.name())
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_throw_an_error_if_we_expect_a_integer_and_double_type() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Integer> got = client.getIntegerDetails("double_key", 200, this.evaluationContext);
        FlagEvaluationDetails<Integer> want = FlagEvaluationDetails.<Integer>builder()
                .value(200)
                .reason(Reason.ERROR.name())
                .errorMessage(
                        "Flag value double_key had unexpected type class java.lang.Double, expected class java.lang.Integer.")
                .errorCode(ErrorCode.TYPE_MISMATCH)
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_double_flag_with_TARGETING_MATCH_reason() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Double> got = client.getDoubleDetails("double_key", 200.20, this.evaluationContext);
        FlagEvaluationDetails<Double> want = FlagEvaluationDetails.<Double>builder()
                .value(100.25)
                .reason(Reason.TARGETING_MATCH.name())
                .variant("True")
                .flagMetadata(defaultMetadata)
                .flagKey("double_key")
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_double_flag_with_TARGETING_MATCH_reason_if_value_point_zero() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Double> got =
                client.getDoubleDetails("double_point_zero_key", 200.20, this.evaluationContext);
        FlagEvaluationDetails<Double> want = FlagEvaluationDetails.<Double>builder()
                .value(100.0)
                .reason(Reason.TARGETING_MATCH.name())
                .variant("True")
                .flagMetadata(defaultMetadata)
                .flagKey("double_point_zero_key")
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_use_double_default_value_if_the_flag_is_disabled() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Double> got = client.getDoubleDetails("disabled", 200.23, this.evaluationContext);
        FlagEvaluationDetails<Double> want = FlagEvaluationDetails.<Double>builder()
                .value(200.23)
                .variant("defaultSdk")
                .flagKey("disabled")
                .reason(Reason.DISABLED.name())
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_value_flag_with_TARGETING_MATCH_reason() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Value> got = client.getObjectDetails("object_key", new Value(), this.evaluationContext);
        FlagEvaluationDetails<Value> want = FlagEvaluationDetails.<Value>builder()
                .value(new Value(new MutableStructure()
                        .add("test", "test1")
                        .add("test2", false)
                        .add("test3", 123.3)
                        .add("test4", 1)))
                .reason(Reason.TARGETING_MATCH.name())
                .variant("True")
                .flagMetadata(defaultMetadata)
                .flagKey("object_key")
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_wrap_into_value_if_wrong_type() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Value> got = client.getObjectDetails("string_key", new Value(), this.evaluationContext);
        FlagEvaluationDetails<Value> want = FlagEvaluationDetails.<Value>builder()
                .value(new Value("CC0000"))
                .reason(Reason.TARGETING_MATCH.name())
                .variant("True")
                .flagMetadata(defaultMetadata)
                .flagKey("string_key")
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_throw_an_error_if_no_targeting_key() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Value> got =
                client.getObjectDetails("string_key", new Value("CC0000"), new MutableContext());
        FlagEvaluationDetails<Value> want = FlagEvaluationDetails.<Value>builder()
                .value(new Value("CC0000"))
                .errorCode(ErrorCode.TARGETING_KEY_MISSING)
                .reason(Reason.ERROR.name())
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_resolve_a_valid_value_flag_with_a_list() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Value> got = client.getObjectDetails("list_key", new Value(), this.evaluationContext);
        FlagEvaluationDetails<Value> want = FlagEvaluationDetails.<Value>builder()
                .value(new Value(new ArrayList<>(Arrays.asList(
                        new Value("test"),
                        new Value("test1"),
                        new Value("test2"),
                        new Value("false"),
                        new Value("test3")))))
                .reason(Reason.TARGETING_MATCH.name())
                .variant("True")
                .flagMetadata(defaultMetadata)
                .flagKey("list_key")
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_not_fail_if_receive_an_unknown_field_in_response() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> got = client.getBooleanDetails("unknown_field", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want = FlagEvaluationDetails.<Boolean>builder()
                .value(true)
                .variant("True")
                .flagKey("unknown_field")
                .reason(Reason.TARGETING_MATCH.name())
                .flagMetadata(defaultMetadata)
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_not_fail_if_no_metadata_in_response() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> got = client.getBooleanDetails("no_metadata", false, this.evaluationContext);
        FlagEvaluationDetails<Boolean> want = FlagEvaluationDetails.<Boolean>builder()
                .value(true)
                .variant("True")
                .flagKey("no_metadata")
                .reason(Reason.TARGETING_MATCH.name())
                .build();
        assertEquals(want, got);
    }

    @SneakyThrows
    @Test
    void should_publish_events() {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .enableCache(true)
                .flushIntervalMs(150L)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        client.getBooleanDetails("fail_500", false, this.evaluationContext);
        Thread.sleep(170L);
        assertEquals(1, publishEventsRequestsReceived, "We should have 1 event waiting to be publish");
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        Thread.sleep(50L);
        assertEquals(
                1,
                publishEventsRequestsReceived,
                "Nothing should be added in the waiting to be published list (stay to 1)");
        Thread.sleep(100);
        assertEquals(3, publishEventsRequestsReceived, "We pass the flush interval, we should have 3 events");
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        Thread.sleep(150);
        assertEquals(
                6,
                publishEventsRequestsReceived,
                "we have call 6 time more, so we should consider only those new calls");
    }

    @SneakyThrows
    @Test
    void should_publish_events_context_without_anonymous() {
        this.evaluationContext = new MutableContext("d45e303a-38c2-11ed-a261-0242ac120002");
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .enableCache(true)
                .flushIntervalMs(50L)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        client.getBooleanDetails("fail_500", false, this.evaluationContext);
        Thread.sleep(100L);
        assertEquals(1, publishEventsRequestsReceived, "We should have 1 event waiting to be publish");
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        Thread.sleep(100);
        assertEquals(3, publishEventsRequestsReceived, "We pass the flush interval, we should have 3 events");
    }

    @SneakyThrows
    @Test
    void should_not_get_cached_value_if_flag_configuration_changed() {
        this.evaluationContext = new MutableContext("d45e303a-38c2-11ed-a261-0242ac120002");
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .disableDataCollection(true)
                .enableCache(true)
                .flagChangePollingIntervalMs(50L)
                .disableDataCollection(true)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        FlagEvaluationDetails<Boolean> got =
                client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        assertEquals(Reason.TARGETING_MATCH.name(), got.getReason());
        got = client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        assertEquals(Reason.CACHED.name(), got.getReason());
        got = client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        assertEquals(Reason.CACHED.name(), got.getReason());
        Thread.sleep(200L);
        got = client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        assertEquals(Reason.TARGETING_MATCH.name(), got.getReason());
    }

    @SneakyThrows
    @Test
    void should_stop_calling_flag_change_if_receive_404() {
        this.flagChanged404 = true;
        this.evaluationContext = new MutableContext("d45e303a-38c2-11ed-a261-0242ac120002");
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .enableCache(true)
                .flagChangePollingIntervalMs(10L)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        Thread.sleep(150L);
        assertEquals(1, this.flagChangeCallCounter);
    }

    @SneakyThrows
    @Test
    void should_send_exporter_metadata() {
        Map<String, Object> customExporterMetadata = new HashMap<>();
        customExporterMetadata.put("version", "1.0.0");
        customExporterMetadata.put("intTest", 1234567890);
        customExporterMetadata.put("doubleTest", 12345.67890);
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .enableCache(true)
                .flushIntervalMs(150L)
                .exporterMetadata(customExporterMetadata)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        Thread.sleep(150);

        Map<String, Object> want = new HashMap<>();
        want.put("version", "1.0.0");
        want.put("intTest", 1234567890);
        want.put("doubleTest", 12345.6789);
        want.put("openfeature", true);
        want.put("provider", "java");
        assertEquals(
                want,
                this.exporterMetadata,
                "we should have the exporter metadata in the last event sent to the data collector");
    }

    @SneakyThrows
    @Test
    void should_add_exporter_metadata_into_evaluation_call() {
        Map<String, Object> customExporterMetadata = new HashMap<>();
        customExporterMetadata.put("version", "1.0.0");
        customExporterMetadata.put("intTest", 1234567890);
        customExporterMetadata.put("doubleTest", 12345.67890);
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(this.baseUrl.toString())
                .timeout(1000)
                .enableCache(true)
                .flushIntervalMs(150L)
                .exporterMetadata(customExporterMetadata)
                .build());
        String providerName = this.testName;
        OpenFeatureAPI.getInstance().setProviderAndWait(providerName, g);
        Client client = OpenFeatureAPI.getInstance().getClient(providerName);
        client.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
        ObjectMapper objectMapper = new ObjectMapper();
        String want = objectMapper
                .readValue(
                        "{ \"user\" : { \"key\" : \"d45e303a-38c2-11ed-a261-0242ac120002\", "
                                + "\"anonymous\" : false, \"custom\" : { \"firstname\" : \"john\", \"gofeatureflag\" : { "
                                + "\"exporterMetadata\" : { \"openfeature\" : true, \"provider\" : \"java\", \"doubleTest\" : 12345.6789, "
                                + "\"intTest\" : 1234567890, \"version\" : \"1.0.0\" } }, \"rate\" : 3.14, \"targetingKey\" : "
                                + "\"d45e303a-38c2-11ed-a261-0242ac120002\", \"company_info\" : { \"size\" : 120, \"name\" : \"my_company\" }, "
                                + "\"email\" : \"john.doe@gofeatureflag.org\", \"age\" : 30, \"lastname\" : \"doe\", \"professional\" : true, "
                                + "\"labels\" : [ \"pro\", \"beta\" ] } }, \"defaultValue\" : false }",
                        Object.class)
                .toString();
        String got = objectMapper
                .readValue(this.requests.get(0).getBody().readString(Charset.defaultCharset()), Object.class)
                .toString();
        assertEquals(want, got, "we should have the exporter metadata in the last event sent to the data collector");
    }

    private String readMockResponse(String filename) throws Exception {
        URL url = getClass().getClassLoader().getResource("mock_responses/" + filename);
        assert url != null;
        byte[] bytes = Files.readAllBytes(Paths.get(url.toURI()));
        return new String(bytes);
    }
}
