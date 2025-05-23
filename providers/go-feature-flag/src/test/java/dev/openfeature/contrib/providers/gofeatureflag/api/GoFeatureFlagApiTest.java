package dev.openfeature.contrib.providers.gofeatureflag.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.TestUtils;
import dev.openfeature.contrib.providers.gofeatureflag.bean.FeatureEvent;
import dev.openfeature.contrib.providers.gofeatureflag.bean.Flag;
import dev.openfeature.contrib.providers.gofeatureflag.bean.FlagConfigResponse;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.bean.IEvent;
import dev.openfeature.contrib.providers.gofeatureflag.bean.Rule;
import dev.openfeature.contrib.providers.gofeatureflag.bean.TrackingEvent;
import dev.openfeature.contrib.providers.gofeatureflag.exception.FlagConfigurationEndpointNotFound;
import dev.openfeature.contrib.providers.gofeatureflag.exception.ImpossibleToRetrieveConfiguration;
import dev.openfeature.contrib.providers.gofeatureflag.exception.ImpossibleToSendEventsException;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.util.GoffApiMock;
import dev.openfeature.sdk.MutableTrackingEventDetails;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.InvalidContextError;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@Slf4j
public class GoFeatureFlagApiTest {
    private MockWebServer server;
    private GoffApiMock goffAPIMock;
    private HttpUrl baseUrl;

    @BeforeEach
    void beforeEach(TestInfo testInfo) throws IOException {
        this.server = new MockWebServer();
        goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.DEFAULT);
        this.server.setDispatcher(goffAPIMock.dispatcher);
        this.server.start();
        baseUrl = server.url("");
    }

    @AfterEach
    void afterEach() throws IOException {
        this.server.close();
        this.server = null;
        baseUrl = null;
    }

    @Nested
    class Constructor {
        @SneakyThrows
        @DisplayName("should throw invalid options if endpoint missing")
        @Test
        public void shouldThrowInvalidOptionsIfEndpointMissing() {
            val options = GoFeatureFlagProviderOptions.builder().build();
            assertThrows(
                    InvalidEndpoint.class,
                    () -> GoFeatureFlagApi.builder().options(options).build());
        }

        @SneakyThrows
        @DisplayName("should throw invalid options if endpoint empty")
        @Test
        public void shouldThrowInvalidOptionsIfEndpointEmpty() {
            val options = GoFeatureFlagProviderOptions.builder().endpoint("").build();
            assertThrows(
                    InvalidEndpoint.class,
                    () -> GoFeatureFlagApi.builder().options(options).build());
        }

        @SneakyThrows
        @DisplayName("should throw invalid options if endpoint invalid")
        @Test
        public void shouldThrowInvalidOptionsIfEndpointInvalid() {
            val options =
                    GoFeatureFlagProviderOptions.builder().endpoint("ccccc").build();
            assertThrows(
                    InvalidEndpoint.class,
                    () -> GoFeatureFlagApi.builder().options(options).build());
        }
    }

    @Nested
    class EvaluateFlag {
        @SneakyThrows
        @DisplayName("request should call the ofrep endpoint")
        @Test
        public void requestShouldCallTheOfrepEndpoint() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            api.evaluateFlag("flag-key", TestUtils.defaultEvaluationContext);

            val want = "/ofrep/v1/evaluate/flags/flag-key";
            assertEquals(want, server.takeRequest().getPath());
        }

        @SneakyThrows
        @DisplayName("request should have an api key")
        @Test
        public void requestShouldHaveAnAPIKey() {
            val apiKey = "my-api-key";
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .apiKey(apiKey)
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            api.evaluateFlag("flag-key", TestUtils.defaultEvaluationContext);

            val want = Const.BEARER_TOKEN + apiKey;
            assertEquals(want, server.takeRequest().getHeader("Authorization"));
        }

        @SneakyThrows
        @DisplayName("request should not set an api key if empty")
        @Test
        public void requestShouldNotSetAnAPIKeyIfEmpty() {
            val apiKey = "";
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .apiKey(apiKey)
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            api.evaluateFlag("flag-key", TestUtils.defaultEvaluationContext);
            assertNull(server.takeRequest().getHeader("Authorization"));
        }

        @SneakyThrows
        @DisplayName("request should have the evaluation context in the body")
        @Test
        public void requestShouldHaveTheEvaluationContextInTheBody() {
            val apiKey = "my-api-key";
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
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
            val gotStr = goffAPIMock.getLastRequestBody();
            ObjectMapper objectMapper = new ObjectMapper();
            Object want = objectMapper.readTree(wantStr);
            Object got = objectMapper.readTree(gotStr);
            assertEquals(want, got, "The JSON strings are not equal");
        }

        @SneakyThrows
        @DisplayName("request should have the default headers")
        @Test
        public void requestShouldHaveDefaultHeaders() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            api.evaluateFlag("flag-key", TestUtils.defaultEvaluationContext);

            val got = server.takeRequest().getHeaders();
            assertEquals("application/json; charset=utf-8", got.get(Const.HTTP_HEADER_CONTENT_TYPE));
        }

        @SneakyThrows
        @DisplayName("should error if timeout is reached")
        @Test
        public void shouldErrorIfTimeoutIsReached() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .timeout(200)
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            assertThrows(GeneralError.class, () -> api.evaluateFlag("timeout", TestUtils.defaultEvaluationContext));
        }

        @SneakyThrows
        @DisplayName("should error if response is a 401")
        @Test
        public void shouldErrorIfResponseIsA401() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            assertThrows(GeneralError.class, () -> api.evaluateFlag("401", TestUtils.defaultEvaluationContext));
        }

        @SneakyThrows
        @DisplayName("should error if response is a 403")
        @Test
        public void shouldErrorIfResponseIsA403() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            assertThrows(GeneralError.class, () -> api.evaluateFlag("403", TestUtils.defaultEvaluationContext));
        }

        @SneakyThrows
        @DisplayName("should error if response has invalid JSON")
        @Test
        public void shouldErrorIfResponseHasInvalidJson() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            assertThrows(
                    GeneralError.class, () -> api.evaluateFlag("invalid-json", TestUtils.defaultEvaluationContext));
        }

        @SneakyThrows
        @DisplayName("should error if response is a 400")
        @Test
        public void shouldErrorIfResponseIsA400() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            assertThrows(InvalidContextError.class, () -> api.evaluateFlag("400", TestUtils.defaultEvaluationContext));
        }

        @SneakyThrows
        @DisplayName("should error if response is a 500")
        @Test
        public void shouldErrorIfResponseIsA500() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            assertThrows(GeneralError.class, () -> api.evaluateFlag("500", TestUtils.defaultEvaluationContext));
        }

        @SneakyThrows
        @DisplayName("should have a valid evaluate response")
        @Test
        public void shouldHaveAValidEvaluateResponse() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
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

    @Nested
    class SendEventToDataCollector {
        @SneakyThrows
        @DisplayName("request should have an api key")
        @Test
        public void requestShouldHaveAnAPIKey() {
            val apiKey = "my-api-key";
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .apiKey(apiKey)
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();

            List<IEvent> events = new ArrayList<>();
            Map<String, Object> exporterMetadata = new HashMap<>();
            api.sendEventToDataCollector(events, exporterMetadata);

            val want = Const.BEARER_TOKEN + apiKey;
            assertEquals(want, server.takeRequest().getHeader("Authorization"));
        }

        @SneakyThrows
        @DisplayName("request should call the collector endpoint")
        @Test
        public void requestShouldCallTheCollectorEndpoint() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            List<IEvent> events = new ArrayList<>();
            Map<String, Object> exporterMetadata = new HashMap<>();
            api.sendEventToDataCollector(events, exporterMetadata);

            val want = "/v1/data/collector";
            assertEquals(want, server.takeRequest().getPath());
        }

        @SneakyThrows
        @DisplayName("request should not set an api key if empty")
        @Test
        public void requestShouldNotSetAnAPIKeyIfEmpty() {
            val apiKey = "";
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .apiKey(apiKey)
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            List<IEvent> events = new ArrayList<>();
            Map<String, Object> exporterMetadata = new HashMap<>();
            api.sendEventToDataCollector(events, exporterMetadata);
            assertNull(server.takeRequest().getHeader("Authorization"));
        }

        @SneakyThrows
        @DisplayName("request should have the default headers")
        @Test
        public void requestShouldHaveDefaultHeaders() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            List<IEvent> events = new ArrayList<>();
            Map<String, Object> exporterMetadata = new HashMap<>();
            api.sendEventToDataCollector(events, exporterMetadata);

            val got = server.takeRequest().getHeaders();
            assertEquals("application/json; charset=utf-8", got.get(Const.HTTP_HEADER_CONTENT_TYPE));
        }

        @SneakyThrows
        @DisplayName("request should have events in the body")
        @Test
        public void requestShouldHaveTheEvaluationContextInTheBody() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            List<IEvent> events = new ArrayList<>();
            events.add(FeatureEvent.builder()
                    .key("xxx")
                    .creationDate(1617970547L)
                    .contextKind("anonymousUser")
                    .kind("feature")
                    .userKey("ABCD")
                    .variation("enabled")
                    .value(true)
                    .defaultValue(false)
                    .build());

            val trackingEvent = new MutableTrackingEventDetails();
            trackingEvent.add("toto", 123);
            events.add(TrackingEvent.builder()
                    .creationDate(1617970548L)
                    .evaluationContext(TestUtils.defaultEvaluationContext.asObjectMap())
                    .key("xxx")
                    .kind("tracking")
                    .contextKind("anonymousUser")
                    .userKey("ABCD")
                    .trackingEventDetails(trackingEvent.asObjectMap())
                    .build());

            Map<String, Object> exporterMetadata = new HashMap<>();
            exporterMetadata.put("provider", "go-feature-flag");
            exporterMetadata.put("intValue", 1);
            api.sendEventToDataCollector(events, exporterMetadata);

            val wantStr = TestUtils.readMockResponse("api_events/", "valid-response.json");
            val gotStr = goffAPIMock.getLastRequestBody();
            ObjectMapper objectMapper = new ObjectMapper();
            Object want = objectMapper.readTree(wantStr);
            Object got = objectMapper.readTree(gotStr);
            assertEquals(want, got, "The JSON strings are not equal");
        }

        @SneakyThrows
        @DisplayName("request should return a an error if 401 received")
        @Test
        public void requestShouldHaveReturn401() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            List<IEvent> events = new ArrayList<>();

            Map<String, Object> exporterMetadata = new HashMap<>();
            exporterMetadata.put("error", 401);
            assertThrows(GeneralError.class, () -> api.sendEventToDataCollector(events, exporterMetadata));
        }

        @SneakyThrows
        @DisplayName("request should return a an error if 403 received")
        @Test
        public void requestShouldHaveReturn403() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            List<IEvent> events = new ArrayList<>();

            Map<String, Object> exporterMetadata = new HashMap<>();
            exporterMetadata.put("error", 403);
            assertThrows(GeneralError.class, () -> api.sendEventToDataCollector(events, exporterMetadata));
        }

        @SneakyThrows
        @DisplayName("request should return a an error if 400 received")
        @Test
        public void requestShouldHaveReturn400() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            List<IEvent> events = new ArrayList<>();

            Map<String, Object> exporterMetadata = new HashMap<>();
            exporterMetadata.put("error", 400);
            assertThrows(GeneralError.class, () -> api.sendEventToDataCollector(events, exporterMetadata));
        }

        @SneakyThrows
        @DisplayName("should return an error if a JsonException is thrown")
        @Test
        public void requestReturnAnErrorIfAJsonExceptionIsThrown() {
            class CircularRef {
                public CircularRef ref;
            }

            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            List<IEvent> events = new ArrayList<>();
            Map<String, Object> exporterMetadata = new HashMap<>();
            exporterMetadata.put("error", 400);
            CircularRef circularRef = new CircularRef();
            circularRef.ref = circularRef;
            exporterMetadata.put("circularRef", circularRef);
            assertThrows(
                    ImpossibleToSendEventsException.class,
                    () -> api.sendEventToDataCollector(events, exporterMetadata));
        }
    }

    @Nested
    class FlagConfiguration {
        @SneakyThrows
        @DisplayName("request should have an api key")
        @Test
        public void requestShouldHaveAnAPIKey() {
            val apiKey = "my-api-key";
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .apiKey(apiKey)
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            api.retrieveFlagConfiguration(null, Collections.emptyList());

            val want = Const.BEARER_TOKEN + apiKey;
            assertEquals(want, server.takeRequest().getHeader("Authorization"));
        }

        @SneakyThrows
        @DisplayName("request should call the collector endpoint")
        @Test
        public void requestShouldCallTheCollectorEndpoint() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            api.retrieveFlagConfiguration(null, Collections.emptyList());

            val want = "/v1/flag/configuration";
            assertEquals(want, server.takeRequest().getPath());
        }

        @SneakyThrows
        @DisplayName("request should not set an api key if empty")
        @Test
        public void requestShouldNotSetAnAPIKeyIfEmpty() {
            val apiKey = "";
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .apiKey(apiKey)
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            api.retrieveFlagConfiguration(null, Collections.emptyList());
            assertNull(server.takeRequest().getHeader("Authorization"));
        }

        @SneakyThrows
        @DisplayName("request should have the default headers")
        @Test
        public void requestShouldHaveDefaultHeaders() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            api.retrieveFlagConfiguration(null, Collections.emptyList());
            val got = server.takeRequest().getHeaders();
            assertEquals("application/json; charset=utf-8", got.get(Const.HTTP_HEADER_CONTENT_TYPE));
        }

        @SneakyThrows
        @DisplayName("request should have an if-none-match header if a etag is provided")
        @Test
        public void requestShouldHaveAnIfNoneMatchHeaderIfAETagIsProvided() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            api.retrieveFlagConfiguration("xxxx", Collections.emptyList());
            val got = server.takeRequest().getHeaders();
            assertEquals("xxxx", got.get(Const.HTTP_HEADER_IF_NONE_MATCH));
        }

        @SneakyThrows
        @DisplayName("request should have flags in body if flags provided")
        @Test
        public void requestShouldHaveFlagsInBodyIfFlagsProvided() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            api.retrieveFlagConfiguration("xxxx", List.of("flag1", "flag2"));
            val gotStr = goffAPIMock.getLastRequestBody();
            val wantStr = "{\"flags\":[\"flag1\",\"flag2\"]}";
            ObjectMapper objectMapper = new ObjectMapper();
            Object want = objectMapper.readTree(wantStr);
            Object got = objectMapper.readTree(gotStr);
            assertEquals(want, got, "The JSON strings are not equal");
        }

        @SneakyThrows
        @DisplayName("request should return a an error if 401 received")
        @Test
        public void requestShouldHaveReturn401() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            assertThrows(
                    ImpossibleToRetrieveConfiguration.class,
                    () -> api.retrieveFlagConfiguration("401", Collections.emptyList()));
        }

        @SneakyThrows
        @DisplayName("request should return a an error if 403 received")
        @Test
        public void requestShouldHaveReturn403() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            assertThrows(
                    ImpossibleToRetrieveConfiguration.class,
                    () -> api.retrieveFlagConfiguration("403", Collections.emptyList()));
        }

        @SneakyThrows
        @DisplayName("request should return a an error if 400 received")
        @Test
        public void requestShouldHaveReturn400() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            assertThrows(
                    ImpossibleToRetrieveConfiguration.class,
                    () -> api.retrieveFlagConfiguration("400", Collections.emptyList()));
        }

        @SneakyThrows
        @DisplayName("request should return a an error if 500 received")
        @Test
        public void requestShouldHaveReturn500() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            assertThrows(
                    ImpossibleToRetrieveConfiguration.class,
                    () -> api.retrieveFlagConfiguration("500", Collections.emptyList()));
        }

        @SneakyThrows
        @DisplayName("request should return a an error if 404 received")
        @Test
        public void requestShouldHaveReturn404() {
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(baseUrl.toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            assertThrows(
                    FlagConfigurationEndpointNotFound.class,
                    () -> api.retrieveFlagConfiguration("404", Collections.emptyList()));
        }

        @SneakyThrows
        @DisplayName("request should return a valid FlagConfigResponse if 200 received")
        @Test
        public void requestShouldHaveReturn200SimpleFlags() {
            val s = new MockWebServer();
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.SIMPLE_CONFIG);
            s.setDispatcher(goffAPIMock.dispatcher);
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(s.url("").toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            val got = api.retrieveFlagConfiguration("valid", Collections.emptyList());
            val evaluationContextEnrichment = new HashMap<String, Object>();
            evaluationContextEnrichment.put("env", "production");

            val flags = new HashMap<String, Flag>();
            val variations = new HashMap<String, Object>();
            variations.put("on", true);
            variations.put("off", false);
            val rule = new Rule();
            rule.setVariation("off");

            val rule2 = new Rule();
            rule2.setVariation("on");

            val flag1 = new Flag();
            flag1.setVariations(variations);
            flag1.setDefaultRule(rule);

            val flag2 = new Flag();
            flag2.setVariations(variations);
            flag2.setDefaultRule(rule2);

            flags.put("TEST", flag1);
            flags.put("TEST2", flag2);
            val want = FlagConfigResponse.builder()
                    .flags(flags)
                    .etag("valid-flag-config.json")
                    .lastUpdated(new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
                            .parse("Wed, 21 Oct 2015 07:28:00 GMT"))
                    .evaluationContextEnrichment(evaluationContextEnrichment)
                    .build();
            assertEquals(want, got);
        }

        @SneakyThrows
        @DisplayName("request should not return last modified date if invalid header")
        @Test
        public void requestShouldNotReturnLastModifiedDateIfInvalidHeader() {
            val s = new MockWebServer();
            val goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.SIMPLE_CONFIG);
            s.setDispatcher(goffAPIMock.dispatcher);
            val options = GoFeatureFlagProviderOptions.builder()
                    .endpoint(s.url("").toString())
                    .build();
            val api = GoFeatureFlagApi.builder().options(options).build();
            val got = api.retrieveFlagConfiguration("invalid-lastmodified-header", Collections.emptyList());
            val evaluationContextEnrichment = new HashMap<String, Object>();
            evaluationContextEnrichment.put("env", "production");

            val flags = new HashMap<String, Flag>();
            val variations = new HashMap<String, Object>();
            variations.put("on", true);
            variations.put("off", false);
            val rule = new Rule();
            rule.setVariation("off");

            val rule2 = new Rule();
            rule2.setVariation("on");

            val flag1 = new Flag();
            flag1.setVariations(variations);
            flag1.setDefaultRule(rule);

            val flag2 = new Flag();
            flag2.setVariations(variations);
            flag2.setDefaultRule(rule2);

            flags.put("TEST", flag1);
            flags.put("TEST2", flag2);
            val want = FlagConfigResponse.builder()
                    .flags(flags)
                    .etag("valid-flag-config.json")
                    .lastUpdated(null)
                    .evaluationContextEnrichment(evaluationContextEnrichment)
                    .build();
            assertEquals(want, got);
        }
    }
}
