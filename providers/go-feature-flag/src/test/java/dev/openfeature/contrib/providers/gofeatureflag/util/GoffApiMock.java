package dev.openfeature.contrib.providers.gofeatureflag.util;

import dev.openfeature.contrib.providers.gofeatureflag.TestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.val;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

public class GoffApiMock {
    private static final String ofrepResponseDir = "ofrep_evaluate_responses/";
    private final MockMode mode;

    @Getter
    private List<RecordedRequest> collectorRequestsHistory = new ArrayList<>();

    @Getter
    private int collectorCallCount = 0;

    @Getter
    private int configurationCallCount = 0;
    /**
     * lastRequestBody contains the body of the last request.
     */
    @Getter
    private String lastRequestBody = null;

    public final Dispatcher dispatcher = new Dispatcher() {
        @SneakyThrows
        @Override
        public MockResponse dispatch(RecordedRequest request) {
            switch (mode) {
                case ENDPOINT_ERROR:
                    return new MockResponse().setResponseCode(500);
                case API_KEY_MISSING:
                    return new MockResponse().setResponseCode(401);
                case INVALID_API_KEY:
                    return new MockResponse().setResponseCode(403);
            }

            lastRequestBody = request.getBody().readUtf8();
            assert request.getPath() != null;
            // routes are matched with contains() so that an endpoint carrying a path prefix
            // (https://host/gofeatureflagproxy/) still reaches the right handler
            if (request.getPath().contains("/ofrep/v1/evaluate/flags/")) {
                return handleEvaluateFlags(request);
            }
            if (request.getPath().contains("/v1/data/collector")) {
                collectorCallCount++;
                return handleCollector(request);
            }
            if (request.getPath().contains("/v1/flag/configuration")) {
                configurationCallCount++;
                return handleFlagConfiguration(request);
            }

            throw new UnsupportedOperationException("Unsupported request (mock is missing): " + request.getPath());
        }
    };

    public GoffApiMock(final MockMode mode) {
        this.mode = mode;
    }

    @SneakyThrows
    public MockResponse handleEvaluateFlags(RecordedRequest request) {
        assert request.getPath() != null;
        String flagName = request.getPath()
                .substring(
                        request.getPath().indexOf("/ofrep/v1/evaluate/flags/") + "/ofrep/v1/evaluate/flags/".length());
        switch (flagName) {
            case "timeout":
                Thread.sleep(500);
                return new MockResponse()
                        .setResponseCode(200)
                        .setBody(TestUtils.readMockResponse(ofrepResponseDir, flagName + ".json"));
            case "400":
                return new MockResponse()
                        .setResponseCode(400)
                        .setBody(TestUtils.readMockResponse(ofrepResponseDir, flagName + ".json"));
            case "401":
                return new MockResponse().setResponseCode(401);
            case "403":
                return new MockResponse().setResponseCode(403);
            case "404":
                return new MockResponse().setResponseCode(404);
            case "500":
                return new MockResponse()
                        .setResponseCode(500)
                        .setBody(TestUtils.readMockResponse(ofrepResponseDir, flagName + ".json"));
            default:
                try {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setBody(TestUtils.readMockResponse(ofrepResponseDir, flagName + ".json"));
                } catch (Exception e) {
                    return new MockResponse().setResponseCode(404);
                }
        }
    }

    @SneakyThrows
    public MockResponse handleCollector(RecordedRequest request) {
        collectorRequestsHistory.add(request);
        Map<String, Object> reqBody = Const.DESERIALIZE_OBJECT_MAPPER.readValue(getLastRequestBody(), Map.class);
        val meta = (Map<String, Object>) reqBody.get("meta");

        if (meta.get("error") != null) {
            val errorCode = Integer.valueOf(meta.get("error").toString());
            return new MockResponse().setResponseCode(errorCode);
        }

        if (meta.get("errorNull") != null) {
            return new MockResponse().setBody("").setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY);
        }

        return new MockResponse().setResponseCode(200).setBody("{\"ingestedContentCount\":0}");
    }

    @SneakyThrows
    public MockResponse handleFlagConfiguration(RecordedRequest request) {
        var configLocation = "valid-all-types.json";
        switch (mode) {
            case SCHEDULED_ROLLOUT_FLAG_CONFIG:
                configLocation = "valid-scheduled-rollout.json";
                break;
            case ENDPOINT_ERROR_404:
                return new MockResponse().setResponseCode(404);
            case EMPTY_FLAG_CONFIG:
                // a valid configuration that happens to contain no flag
                return new MockResponse()
                        .setResponseCode(200)
                        .setBody("{\"flags\": {}}")
                        .addHeader(Const.HTTP_HEADER_ETAG, "\"empty-flag-config\"")
                        .addHeader(Const.HTTP_HEADER_LAST_MODIFIED, "Wed, 21 Oct 2015 07:28:00 GMT");
            case SERVE_OLD_CONFIGURATION:
                if (configurationCallCount > 1) {
                    // we serve an old configuration after the 1st call.
                    return new MockResponse()
                            .setResponseCode(200)
                            .setBody(TestUtils.readMockResponse("flag_config_responses/", configLocation))
                            .addHeader(Const.HTTP_HEADER_ETAG, "\"different-etag\"")
                            .addHeader(Const.HTTP_HEADER_LAST_MODIFIED, "Wed, 21 Oct 2015 05:28:00 GMT");
                }
                break;
            case CHANGE_CONFIG_AFTER_1ST_EVAL:
                configLocation =
                        configurationCallCount > 1 ? "valid-all-types-config-change.json" : "valid-all-types.json";
                break;
            case NOT_MODIFIED_THEN_CHANGE:
                if (configurationCallCount == 2) {
                    // a 304 on the 2nd call: the polling daemon must survive it and keep polling
                    return new MockResponse().setResponseCode(304);
                }
                configLocation =
                        configurationCallCount > 2 ? "valid-all-types-config-change.json" : "valid-all-types.json";
                break;
            case SIMPLE_CONFIG:
                configLocation = "valid-flag-config.json";
                break;
            default:
                configLocation = "valid-all-types.json";
                break;
        }

        val etag = request.getHeaders().get(Const.HTTP_HEADER_IF_NONE_MATCH);
        if (etag == null) {
            return new MockResponse()
                    .setResponseCode(200)
                    .setBody(TestUtils.readMockResponse("flag_config_responses/", configLocation))
                    .addHeader(Const.HTTP_HEADER_ETAG, "\"" + configLocation + "\"")
                    .addHeader(Const.HTTP_HEADER_LAST_MODIFIED, "Wed, 21 Oct 2015 07:28:00 GMT");
        }
        switch (etag) {
            case "400":
                return new MockResponse().setResponseCode(400);
            case "401":
                return new MockResponse().setResponseCode(401);
            case "403":
                return new MockResponse().setResponseCode(403);
            case "404":
                return new MockResponse().setResponseCode(404);
            case "500":
                return new MockResponse().setResponseCode(500);
            case "unknown-flag-field":
                // a flag carrying fields this provider has no model for: they must survive intact
                return new MockResponse()
                        .setResponseCode(200)
                        .setBody("{\"flags\": {\"TEST\": {\"variations\": {\"on\": true},"
                                + " \"defaultRule\": {\"variation\": \"on\"},"
                                + " \"aFieldFromANewerEngine\": {\"nested\": [1, 2, 3]},"
                                + " \"bucketingKey\": \"teamId\"}}}")
                        .addHeader(Const.HTTP_HEADER_ETAG, "\"unknown-flag-field\"");
            case "no-flags":
                // a 200 whose body carries no flags key at all
                return new MockResponse()
                        .setResponseCode(200)
                        .setBody("{}")
                        .addHeader(Const.HTTP_HEADER_ETAG, "\"an-advanced-etag\"")
                        .addHeader(Const.HTTP_HEADER_LAST_MODIFIED, "Wed, 21 Oct 2015 07:28:00 GMT");
            case "null-flags":
                // a 200 whose flags key is explicitly null
                return new MockResponse()
                        .setResponseCode(200)
                        .setBody("{\"flags\": null, \"evaluationContextEnrichment\": {\"env\": \"production\"}}")
                        .addHeader(Const.HTTP_HEADER_ETAG, "\"an-advanced-etag\"")
                        .addHeader(Const.HTTP_HEADER_LAST_MODIFIED, "Wed, 21 Oct 2015 07:28:00 GMT");
            case "null-body":
                // a 200 whose body is the JSON literal null
                return new MockResponse()
                        .setResponseCode(200)
                        .setBody("null")
                        .addHeader(Const.HTTP_HEADER_ETAG, "\"an-advanced-etag\"");
            case "null-enrichment":
                // a nil Go map marshals to null: valid, and means "no enrichment"
                return new MockResponse()
                        .setResponseCode(200)
                        .setBody("{\"flags\": {\"TEST\": {\"variations\": {\"on\": true, \"off\": false},"
                                + " \"defaultRule\": {\"variation\": \"on\"}}},"
                                + " \"evaluationContextEnrichment\": null}")
                        .addHeader(Const.HTTP_HEADER_ETAG, "\"null-enrichment\"");
            case "304-with-etag":
                // a 304 that echoes the validator back, as the relay proxy does
                return new MockResponse()
                        .setResponseCode(304)
                        .addHeader(Const.HTTP_HEADER_ETAG, "\"" + configLocation + "\"")
                        .addHeader(Const.HTTP_HEADER_LAST_MODIFIED, "Wed, 21 Oct 2015 07:28:00 GMT");
            case "304-without-etag":
                // a 304 carrying no validator at all
                return new MockResponse().setResponseCode(304);
            case "invalid-lastmodified-header":
                return new MockResponse()
                        .setResponseCode(200)
                        .setBody(TestUtils.readMockResponse("flag_config_responses/", configLocation))
                        .addHeader(Const.HTTP_HEADER_ETAG, "\"" + configLocation + "\"")
                        .addHeader(Const.HTTP_HEADER_LAST_MODIFIED, "Wed, 21 Oct 2015 07:2 GMT");
            default:
                return new MockResponse()
                        .setResponseCode(200)
                        .setBody(TestUtils.readMockResponse("flag_config_responses/", configLocation))
                        .addHeader(Const.HTTP_HEADER_ETAG, "\"" + configLocation + "\"")
                        .addHeader(Const.HTTP_HEADER_LAST_MODIFIED, "Wed, 21 Oct 2015 07:28:00 GMT");
        }
    }

    public enum MockMode {
        API_KEY_MISSING,
        INVALID_API_KEY,
        ENDPOINT_ERROR,
        ENDPOINT_ERROR_404,
        CHANGE_CONFIG_AFTER_1ST_EVAL,
        NOT_MODIFIED_THEN_CHANGE,
        SIMPLE_CONFIG,
        DEFAULT,
        SERVE_OLD_CONFIGURATION,
        SCHEDULED_ROLLOUT_FLAG_CONFIG,
        EMPTY_FLAG_CONFIG,
    }
}
