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
import org.jetbrains.annotations.NotNull;

public class GoffApiMock {
    private static final String ofrepResponseDir = "ofrep_evaluate_responses/";
    private final MockMode mode;
    @Getter
    private List<RecordedRequest> collectorRequestsHistory = new ArrayList<>();
    @Getter
    private int collectorCallCount = 0;
    private int configurationCallCount = 0;
    /** lastRequestBody contains the body of the last request. */
    @Getter
    private String lastRequestBody = null;
    public final Dispatcher dispatcher = new Dispatcher() {
        @NotNull
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
            if (request.getPath().startsWith("/ofrep/v1/evaluate/flags/")) {
                return handleEvaluateFlags(request);
            }
            if (request.getPath().startsWith("/v1/data/collector")) {
                collectorCallCount++;
                return handleCollector(request);
            }
            if (request.getPath().startsWith("/v1/flag/configuration")) {
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
        String flagName = request.getPath().replace("/ofrep/v1/evaluate/flags/", "");
        switch (flagName) {
            case "timeout":
                Thread.sleep(500);
                return new MockResponse().setResponseCode(200)
                        .setBody(TestUtils.readMockResponse(ofrepResponseDir, flagName + ".json"));
            case "400":
                return new MockResponse().setResponseCode(400)
                        .setBody(TestUtils.readMockResponse(ofrepResponseDir, flagName + ".json"));
            case "401":
                return new MockResponse().setResponseCode(401);
            case "403":
                return new MockResponse().setResponseCode(403);
            case "404":
                return new MockResponse().setResponseCode(404);
            case "500":
                return new MockResponse().setResponseCode(500)
                        .setBody(TestUtils.readMockResponse(ofrepResponseDir, flagName + ".json"));
            default:
                try {
                    return new MockResponse().setResponseCode(200)
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
            return new MockResponse()
                    .setBody("")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY);
        }

        return new MockResponse().setResponseCode(200)
                .setBody("{\"ingestedContentCount\":0}");
    }

    @SneakyThrows
    public MockResponse handleFlagConfiguration(RecordedRequest request) {
        var configLocation = "valid-all-types.json";
        switch (mode) {
            case ENDPOINT_ERROR_404:
                return new MockResponse().setResponseCode(404);
            case SERVE_OLD_CONFIGURATION:
                if (configurationCallCount > 1) {
                    // we serve an old configuration after the 1st call.
                    return new MockResponse().setResponseCode(200)
                            .setBody(TestUtils.readMockResponse("flag_config_responses/", configLocation))
                            .addHeader(Const.HTTP_HEADER_ETAG, "different-etag")
                            .addHeader(Const.HTTP_HEADER_LAST_MODIFIED, "Wed, 21 Oct 2015 05:28:00 GMT");
                }
                break;
            case CHANGE_CONFIG_AFTER_1ST_EVAL:
                configLocation =
                        configurationCallCount > 1 ? "valid-all-types-config-change.json" : "valid-all-types.json";
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
            return new MockResponse().setResponseCode(200)
                    .setBody(TestUtils.readMockResponse("flag_config_responses/", configLocation))
                    .addHeader(Const.HTTP_HEADER_ETAG, configLocation)
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
            case "invalid-lastmodified-header":
                return new MockResponse().setResponseCode(200)
                        .setBody(TestUtils.readMockResponse("flag_config_responses/", configLocation))
                        .addHeader(Const.HTTP_HEADER_ETAG, configLocation)
                        .addHeader(Const.HTTP_HEADER_LAST_MODIFIED, "Wed, 21 Oct 2015 07:2 GMT");
            default:
                return new MockResponse().setResponseCode(200)
                        .setBody(TestUtils.readMockResponse("flag_config_responses/", configLocation))
                        .addHeader(Const.HTTP_HEADER_ETAG, configLocation)
                        .addHeader(Const.HTTP_HEADER_LAST_MODIFIED, "Wed, 21 Oct 2015 07:28:00 GMT");
        }
    }

    public enum MockMode {
        API_KEY_MISSING,
        INVALID_API_KEY,
        ENDPOINT_ERROR,
        ENDPOINT_ERROR_404,
        CHANGE_CONFIG_AFTER_1ST_EVAL,
        SIMPLE_CONFIG,
        DEFAULT,
        SERVE_OLD_CONFIGURATION,
    }
}
