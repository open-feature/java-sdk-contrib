package dev.openfeature.contrib.providers.unleash;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import io.getunleash.UnleashContext;
import io.getunleash.UnleashException;
import io.getunleash.event.ToggleEvaluated;
import io.getunleash.event.UnleashEvent;
import io.getunleash.event.UnleashSubscriber;
import io.getunleash.repository.FeatureToggleResponse;
import io.getunleash.util.UnleashConfig;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

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
 * UnleashProvider test, based on APIs mocking.
 * Inspired by Unleash tests.
 */
@WireMockTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnleashProviderTest {

    public static final String FLAG_NAME = "variant-flag";
    public static final String VARIANT_FLAG_NAME = "variant-flag";
    public static final String VARIANT_FLAG_VALUE = "v1";
    public static final String INT_FLAG_NAME = "int-flag";
    public static final Integer INT_FLAG_VALUE = 123;
    public static final String DOUBLE_FLAG_NAME = "double-flag";
    public static final Double DOUBLE_FLAG_VALUE = 1.23;
    public static final String USERS_FLAG_NAME = "users-flag";
    public static final String JSON_VARIANT_FLAG_NAME = "json-flag";
    public static final String JSON_VARIANT_FLAG_VALUE = "{ a: 1 }";
    public static final String CSV_VARIANT_FLAG_NAME = "csv-flag";
    public static final String CSV_VARIANT_FLAG_VALUE = "a,b,c";
    private static UnleashProvider unleashProvider;
    private static Client client;

    @BeforeAll
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(any(anyUrl()).willReturn(aResponse()
            .withStatus(200)
            .withBody("{}")));
        String unleashAPI = "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/api/";
        String backupFileContent = readBackupFile();
        mockUnleashAPI(backupFileContent);
        unleashProvider = buildUnleashProvider(true, unleashAPI, backupFileContent, new TestSubscriber());
        OpenFeatureAPI.getInstance().setProviderAndWait("sync", unleashProvider);
        client = OpenFeatureAPI.getInstance().getClient("sync");
    }

    @AfterAll
    public void shutdown() {
        unleashProvider.shutdown();
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
    private UnleashProvider buildUnleashProvider(boolean synchronousFetchOnInitialisation, String unleashAPI, String backupFileContent, TestSubscriber testSubscriber) {
        UnleashConfig.Builder unleashConfigBuilder =
            UnleashConfig.builder().unleashAPI(new URI(unleashAPI))
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
    void getIntegerEvaluation() {
        UnleashContext unleashContext = UnleashContext.builder().userId("int").build();
        EvaluationContext evaluationContext = ContextTransformer.transform(unleashContext);
        assertEquals(INT_FLAG_VALUE, unleashProvider.getIntegerEvaluation(INT_FLAG_NAME, 1,
            evaluationContext).getValue());
        assertEquals(INT_FLAG_VALUE, client.getIntegerValue(INT_FLAG_NAME, 1));
        assertEquals(1, client.getIntegerValue("non-existing", 1));

        // non-number flag value
        assertEquals(1, client.getIntegerValue(VARIANT_FLAG_NAME, 1));
    }

    @Test
    void getDoubleEvaluation() {
        UnleashContext unleashContext = UnleashContext.builder().userId("double").build();
        EvaluationContext evaluationContext = ContextTransformer.transform(unleashContext);
        assertEquals(DOUBLE_FLAG_VALUE, unleashProvider.getDoubleEvaluation(DOUBLE_FLAG_NAME, 1.1,
            evaluationContext).getValue());
        assertEquals(DOUBLE_FLAG_VALUE, client.getDoubleValue(DOUBLE_FLAG_NAME, 1.1));
        assertEquals(1.1, client.getDoubleValue("non-existing", 1.1));

        // non-number flag value
        assertEquals(1.1, client.getDoubleValue(VARIANT_FLAG_NAME, 1.1));
    }

    @Test
    void getJsonVariantEvaluation() {
        assertEquals(JSON_VARIANT_FLAG_VALUE, unleashProvider.getObjectEvaluation(JSON_VARIANT_FLAG_NAME, new Value(""),
            new ImmutableContext()).getValue().asString());
        assertEquals(new Value(JSON_VARIANT_FLAG_VALUE), client.getObjectValue(JSON_VARIANT_FLAG_NAME, new Value("")));
        assertEquals("fallback_str", unleashProvider.getObjectEvaluation("non-existing",
            new Value("fallback_str"), new ImmutableContext()).getValue().asString());
        assertEquals(new Value("fallback_str"), client.getObjectValue("non-existing", new Value("fallback_str")));
    }

    @Test
    void getCSVVariantEvaluation() {
        assertEquals(CSV_VARIANT_FLAG_VALUE, unleashProvider.getObjectEvaluation(CSV_VARIANT_FLAG_NAME, new Value(""),
            new ImmutableContext()).getValue().asString());
        assertEquals(new Value(CSV_VARIANT_FLAG_VALUE), client.getObjectValue(CSV_VARIANT_FLAG_NAME, new Value("")));
        assertEquals("fallback_str", unleashProvider.getObjectEvaluation("non-existing",
            new Value("fallback_str"), new ImmutableContext()).getValue().asString());
        assertEquals(new Value("fallback_str"), client.getObjectValue("non-existing", new Value("fallback_str")));
    }

    @Test
    void getBooleanEvaluationByUser() {
        UnleashContext unleashContext = UnleashContext.builder().userId("111").build();
        EvaluationContext evaluationContext = ContextTransformer.transform(unleashContext);
        assertEquals(true, unleashProvider.getBooleanEvaluation(USERS_FLAG_NAME, false, evaluationContext).getValue());
        assertEquals(true, client.getBooleanValue(USERS_FLAG_NAME, false, evaluationContext));
        unleashContext = UnleashContext.builder().userId("2").build();
        evaluationContext = ContextTransformer.transform(unleashContext);
        assertEquals(false, unleashProvider.getBooleanEvaluation(USERS_FLAG_NAME, false, evaluationContext).getValue());
        assertEquals(false, client.getBooleanValue(USERS_FLAG_NAME, false, evaluationContext));
    }

    @Test
    void getEvaluationMetadataTest() {
        ProviderEvaluation<String> stringEvaluation = unleashProvider.getStringEvaluation(VARIANT_FLAG_NAME, "",
            new ImmutableContext());
        ImmutableMetadata flagMetadata = stringEvaluation.getFlagMetadata();
        assertEquals("default", flagMetadata.getString("variant-stickiness"));
        assertEquals("string", flagMetadata.getString("payload-type"));
        assertEquals(true, flagMetadata.getBoolean("enabled"));
        ProviderEvaluation<String> nonExistingFlagEvaluation = unleashProvider.getStringEvaluation("non-existing",
            "", new ImmutableContext());
        assertEquals(false, nonExistingFlagEvaluation.getFlagMetadata().getBoolean("enabled"));
    }

    @SneakyThrows
    @Test
    void shouldThrowIfNotInitialized() {
        UnleashProvider asyncInitUnleashProvider = buildUnleashProvider(false, "http://fakeAPI", "{}", new TestSubscriber());
        assertEquals(ProviderState.NOT_READY, asyncInitUnleashProvider.getState());

        // ErrorCode.PROVIDER_NOT_READY should be returned when evaluated via the client
        assertThrows(ProviderNotReadyError.class, ()-> asyncInitUnleashProvider.getBooleanEvaluation("fail_not_initialized", false, new ImmutableContext()));
        assertThrows(ProviderNotReadyError.class, ()-> asyncInitUnleashProvider.getStringEvaluation("fail_not_initialized", "", new ImmutableContext()));

        asyncInitUnleashProvider.initialize(null);
        assertThrows(GeneralError.class, ()-> asyncInitUnleashProvider.initialize(null));

        asyncInitUnleashProvider.shutdown();
    }

    @SneakyThrows
    @Test
    void shouldThrowIfErrorEvent() {
        UnleashProvider asyncInitUnleashProvider = buildUnleashProvider(false, "http://fakeAPI", "{}", null);
        asyncInitUnleashProvider.initialize(new ImmutableContext());

        asyncInitUnleashProvider.emitProviderError(ProviderEventDetails.builder().build());

        // ErrorCode.PROVIDER_NOT_READY should be returned when evaluated via the client
        assertThrows(GeneralError.class, ()-> asyncInitUnleashProvider.getBooleanEvaluation("fail", false, new ImmutableContext()));
        assertThrows(GeneralError.class, ()-> asyncInitUnleashProvider.getStringEvaluation("fail", "", new ImmutableContext()));

        asyncInitUnleashProvider.shutdown();
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

    @SneakyThrows
    @Test
    void subscriberWrapperTest() {
        UnleashProvider asyncInitUnleashProvider = buildUnleashProvider(false,
    "http://fakeAPI", "{}", null);
        UnleashSubscriberWrapper unleashSubscriberWrapper = new UnleashSubscriberWrapper(
            new TestSubscriber(), asyncInitUnleashProvider);
        unleashSubscriberWrapper.clientMetrics(null);
        unleashSubscriberWrapper.clientRegistered(null);
        unleashSubscriberWrapper.featuresBackedUp(null);
        unleashSubscriberWrapper.featuresBackupRestored(null);
        unleashSubscriberWrapper.featuresBootstrapped(null);
        unleashSubscriberWrapper.impression(null);
        unleashSubscriberWrapper.toggleEvaluated(new ToggleEvaluated("dummy", false));
        unleashSubscriberWrapper.togglesFetched(new FeatureToggleResponse(FeatureToggleResponse.Status.NOT_CHANGED,
200));
        unleashSubscriberWrapper.toggleBackupRestored(null);
        unleashSubscriberWrapper.togglesBackedUp(null);
        unleashSubscriberWrapper.togglesBootstrapped(null);
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