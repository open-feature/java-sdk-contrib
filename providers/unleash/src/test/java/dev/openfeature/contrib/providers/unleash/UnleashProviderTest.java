package dev.openfeature.contrib.providers.unleash;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import io.getunleash.UnleashContext;
import io.getunleash.UnleashException;
import io.getunleash.event.ToggleEvaluated;
import io.getunleash.event.UnleashEvent;
import io.getunleash.event.UnleashSubscriber;
import io.getunleash.repository.FeatureToggleResponse;
import io.getunleash.util.UnleashConfig;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UnleashProvider Test.
 * Inspired by Unleash tests.
 */
@WireMockTest
class UnleashProviderTest {

    public static final String FLAG_NAME = "Demo";
    public static final String VARIANT_FLAG_NAME = "new-api";
    public static final String VARIANT_FLAG_VALUE = "v1";
    private TestSubscriber testSubscriber;
    private UnleashProvider unleashProvider;
    private Client client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        testSubscriber = new TestSubscriber();
        stubFor(any(anyUrl()).willReturn(aResponse()
            .withStatus(200)
            .withBody("{}")));
        String unleashAPI = "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/api/";
        String backupFileContent = readBackupFile();
        mockUnleashAPI(backupFileContent);
        unleashProvider = buildUnleashProvider(true, unleashAPI, backupFileContent);
        OpenFeatureAPI.getInstance().setProviderAndWait("sync", unleashProvider);
        client = OpenFeatureAPI.getInstance().getClient("sync");
    }

    private void mockUnleashAPI(String backupFileContent) {
        stubFor(
            get(urlEqualTo("/api/client/features"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(backupFileContent)));
        stubFor(post(urlEqualTo("/api/client/register")).willReturn(aResponse().withStatus(200)));
    }

    @SneakyThrows
    private UnleashProvider buildUnleashProvider(boolean synchronousFetchOnInitialisation, String unleashAPI, String backupFileContent) {
        TestSubscriber testSubscriber = new TestSubscriber();
        UnleashConfig.Builder unleashConfigBuilder =
            UnleashConfig.builder()
                .unleashAPI(new URI(unleashAPI))
                .appName("fakeApp")
                .subscriber(testSubscriber)
                .synchronousFetchOnInitialisation(synchronousFetchOnInitialisation);

        UnleashOptions unleashOptions = UnleashOptions.builder()
            .unleashConfigBuilder(unleashConfigBuilder)
            .build();
        return new UnleashProvider(unleashOptions);
    }

    @SneakyThrows
    private String readBackupFile() {
        URL url = getClass().getResource("/features.json");
        return new String(Files.readAllBytes(Paths.get(url.toURI())));
    }

    @Test
    void getBooleanEvaluation() {
        assertEquals(true, unleashProvider.getBooleanEvaluation(FLAG_NAME, false, new ImmutableContext()).getValue());
        assertEquals(true, client.getBooleanValue(FLAG_NAME, false));
        assertEquals(false, unleashProvider.getBooleanEvaluation("non-existing", false, new ImmutableContext()).getValue());
        assertEquals(false, client.getBooleanValue("non-existing", false));
    }

    @Test
    void getStringVariantEvaluation() {
        assertEquals(VARIANT_FLAG_VALUE, unleashProvider.getStringEvaluation(VARIANT_FLAG_NAME, "",
            new ImmutableContext()).getValue());
        assertEquals(VARIANT_FLAG_VALUE, client.getStringValue(VARIANT_FLAG_NAME, ""));
        assertEquals("fallback_str", unleashProvider.getStringEvaluation("non-existing",
            "fallback_str", new ImmutableContext()).getValue());
        assertEquals("fallback_str", client.getStringValue("non-existing", "fallback_str"));
    }

    @Test
    void getBooleanEvaluationByUser() {
        String flagName = "by-users";
        UnleashContext unleashContext = UnleashContext.builder().userId("111").build();
        EvaluationContext evaluationContext = ContextTransformer.transform(unleashContext);
        assertEquals(true, unleashProvider.getBooleanEvaluation(flagName, false, evaluationContext).getValue());
        assertEquals(true, client.getBooleanValue(flagName, false, evaluationContext));
        unleashContext = UnleashContext.builder().userId("2").build();
        evaluationContext = ContextTransformer.transform(unleashContext);
        assertEquals(false, unleashProvider.getBooleanEvaluation(flagName, false, evaluationContext).getValue());
        assertEquals(false, client.getBooleanValue(flagName, false, evaluationContext));
    }

    @Test
    void typeMismatch() {
        assertThrows(TypeMismatchError.class, () -> {
            unleashProvider.getIntegerEvaluation("test", 1, new ImmutableContext());
        });
    }

    @SneakyThrows
    @Test
    void asyncInitTest() {
        UnleashProvider asyncInitUnleashProvider = buildUnleashProvider(false, "http://fakeAPI", "");
        OpenFeatureAPI.getInstance().setProvider("async", asyncInitUnleashProvider);
        assertEquals(ProviderState.NOT_READY, asyncInitUnleashProvider.getState());

        // ErrorCode.PROVIDER_NOT_READY should be returned when evaluated via the client
        assertThrows(ProviderNotReadyError.class, ()-> asyncInitUnleashProvider.getBooleanEvaluation("fail_not_initialized", false, new ImmutableContext()));

        asyncInitUnleashProvider.initialize(new ImmutableContext());
        asyncInitUnleashProvider.emitProviderReady(ProviderEventDetails.builder().build());

        assertEquals(ProviderState.READY, asyncInitUnleashProvider.getState());
        assertEquals(false, asyncInitUnleashProvider.getBooleanEvaluation("non-existing", false, new ImmutableContext()).getValue());
        assertEquals(true, unleashProvider.getBooleanEvaluation(FLAG_NAME, false, new ImmutableContext()).getValue());
        assertEquals(true, client.getBooleanValue(FLAG_NAME, false));

        asyncInitUnleashProvider.emitProviderError(ProviderEventDetails.builder().build());
        assertEquals(ProviderState.ERROR, asyncInitUnleashProvider.getState());
    }

    @SneakyThrows
    @Test
    void contextTransformTest() {
        String appNameValue = "appName_value";
        String userIdValue = "userId_value";
        String environmentValue = "environment_value";
        String remoteAddressValue = "remoteAddress_value";
        String sessionIdValue = "sessionId_value";
        ZonedDateTime currentTimeValue = ZonedDateTime.now();
        String customPropertyValue = "customProperty_value";
        String customPropertyKey = "customProperty";

        UnleashContext unleashContext = UnleashContext.builder()
            .userId(userIdValue)
            .currentTime(currentTimeValue)
            .sessionId(sessionIdValue)
            .remoteAddress(remoteAddressValue)
            .environment(environmentValue)
            .appName(appNameValue)
            .addProperty(customPropertyKey, customPropertyValue)
            .build();
        EvaluationContext evaluationContext = ContextTransformer.transform(unleashContext);

        UnleashContext transformedUnleashContext = ContextTransformer.transform(evaluationContext);
        assertEquals(appNameValue, transformedUnleashContext.getAppName().get());
        assertEquals(userIdValue, transformedUnleashContext.getUserId().get());
        assertEquals(environmentValue, transformedUnleashContext.getEnvironment().get());
        assertEquals(remoteAddressValue, transformedUnleashContext.getRemoteAddress().get());
        assertEquals(sessionIdValue, transformedUnleashContext.getSessionId().get());
        assertEquals(currentTimeValue, transformedUnleashContext.getCurrentTime().get());
        assertEquals(customPropertyValue, transformedUnleashContext.getProperties().get(customPropertyKey));
    }

    private class TestSubscriber implements UnleashSubscriber {

        private FeatureToggleResponse.Status status;

        private String toggleName;
        private boolean toggleEnabled;

        private List<UnleashEvent> events = new ArrayList<>();
        private List<UnleashException> errors = new ArrayList<>();

        @Override
        public void on(UnleashEvent unleashEvent) {
            this.events.add(unleashEvent);
        }

        @Override
        public void onError(UnleashException unleashException) {
            this.errors.add(unleashException);
        }

        @Override
        public void toggleEvaluated(ToggleEvaluated toggleEvaluated) {
            this.toggleName = toggleEvaluated.getToggleName();
            this.toggleEnabled = toggleEvaluated.isEnabled();
        }

        @Override
        public void togglesFetched(FeatureToggleResponse toggleResponse) {
            this.status = toggleResponse.getStatus();
        }
    }
}