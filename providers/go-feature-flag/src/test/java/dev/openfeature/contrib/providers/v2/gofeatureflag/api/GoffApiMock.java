package dev.openfeature.contrib.providers.v2.gofeatureflag.api;

import dev.openfeature.contrib.providers.v2.gofeatureflag.TestUtils;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;

public class GoffApiMock {
    private static final String ofrepResponseDir = "ofrep_evaluate_responses/";
    public final Dispatcher dispatcher = new Dispatcher() {
        @NotNull
        @SneakyThrows
        @Override
        public MockResponse dispatch(RecordedRequest request) {
            assert request.getPath() != null;
            if (request.getPath().startsWith("/ofrep/v1/evaluate/flags/")) {
                return handleEvaluateFlags(request);
            }
            throw new UnsupportedOperationException("Unsupported request (mock is missing): " + request.getPath());
        }
    };

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
            case "500":
                return new MockResponse().setResponseCode(500)
                        .setBody(TestUtils.readMockResponse(ofrepResponseDir, flagName + ".json"));
            default:
                return new MockResponse().setResponseCode(200)
                        .setBody(TestUtils.readMockResponse(ofrepResponseDir, flagName + ".json"));
        }
    }

}
