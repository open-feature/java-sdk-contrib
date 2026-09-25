package dev.openfeature.contrib.providers.gofeatureflag;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.gofeatureflag.util.GoffApiMock;
import dev.openfeature.sdk.OpenFeatureAPI;
import java.io.IOException;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.provider.Arguments;

abstract class AbstractGoFeatureFlagProviderTest {
    /** The three shapes that register no data collector hook, plus the one that does. */
    static Stream<Arguments> shutdownModes() {
        return Stream.of(
                Arguments.of(EvaluationType.IN_PROCESS, false),
                Arguments.of(EvaluationType.IN_PROCESS, true),
                Arguments.of(EvaluationType.REMOTE, false),
                Arguments.of(EvaluationType.REMOTE, true));
    }

    protected MockWebServer server;
    protected GoffApiMock goffAPIMock;
    protected HttpUrl baseUrl;
    protected String testName;

    @BeforeEach
    void beforeEach(TestInfo testInfo) throws IOException {
        this.server = new MockWebServer();
        goffAPIMock = new GoffApiMock(GoffApiMock.MockMode.DEFAULT);
        this.server.setDispatcher(goffAPIMock.dispatcher);
        this.server.start();
        baseUrl = server.url("");
        this.testName = testInfo.getDisplayName();
    }

    @SneakyThrows
    @AfterEach
    void afterEach() throws IOException {
        OpenFeatureAPI.getInstance().shutdown();

        Thread.sleep(50L);
        this.server.close();
        this.server = null;
        baseUrl = null;
    }
}
