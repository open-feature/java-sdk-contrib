package dev.openfeature.contrib.providers.unleash;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Value;
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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UnleashProvider Test.
 * Inspired by Unleash tests.
 */
class UnleashProviderTest {

    public static final String FLAG_NAME = "flagName";
    public static final String VARIANT_1 = "variant1";
    public static final String VARIANT_1_VALUE = "variant1_value";
    private TestSubscriber testSubscriber;
    private UnleashProvider unleashProvider;
    private Client client;

    @BeforeEach
    void setUp() {
        testSubscriber = new TestSubscriber();
        unleashProvider = buildUnleashProvider(true);
        OpenFeatureAPI.getInstance().setProviderAndWait("sync", unleashProvider);
        client = OpenFeatureAPI.getInstance().getClient("sync");
    }

    @SneakyThrows
    private UnleashProvider buildUnleashProvider(boolean synchronousFetchOnInitialisation) {
        TestSubscriber testSubscriber = new TestSubscriber();
        UnleashConfig.Builder unleashConfigBuilder =
            UnleashConfig.builder()
                .unleashAPI("http://fakeAPI")
                .appName("fakeApp")
                .subscriber(testSubscriber)
                .synchronousFetchOnInitialisation(synchronousFetchOnInitialisation);

        UnleashOptions unleashOptions = UnleashOptions.builder()
            .unleashConfigBuilder(unleashConfigBuilder)
            .build();
        return new TestUnleashProvider(unleashOptions);
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
        assertEquals(VARIANT_1_VALUE, unleashProvider.getStringEvaluation(FLAG_NAME, "",
            new ImmutableContext()).getValue());
        assertEquals(VARIANT_1_VALUE, client.getStringValue(FLAG_NAME, ""));
        assertEquals("fallback_str", unleashProvider.getStringEvaluation("non-existing",
            "fallback_str", new ImmutableContext()).getValue());
        assertEquals("fallback_str", client.getStringValue("non-existing", "fallback_str"));
    }

    @Test
    void getBooleanEvaluationByUser() {
        String flagName = "testByUserId";
        UnleashContext unleashContext = UnleashContext.builder().userId("1").build();
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
        UnleashProvider asyncInitUnleashProvider = buildUnleashProvider(false);
        OpenFeatureAPI.getInstance().setProvider("async", asyncInitUnleashProvider);
        Client asyncClient = OpenFeatureAPI.getInstance().getClient("async");
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
        Map<String, Value> values = new HashMap<>();
        String appNameValue = "appName_value";
        values.put("appName", Value.objectToValue(appNameValue));
        String userIdValue = "userId_value";
        values.put("userId", Value.objectToValue(userIdValue));
        String environmentValue = "environment_value";
        values.put("environment", Value.objectToValue(environmentValue));
        String remoteAddressValue = "remoteAddress_value";
        values.put("remoteAddress", Value.objectToValue(remoteAddressValue));
        String sessionIdValue = "sessionId_value";
        values.put("sessionId", Value.objectToValue(sessionIdValue));
        ZonedDateTime currentTimeValue = ZonedDateTime.now();
        values.put("currentTime", Value.objectToValue(currentTimeValue.toString()));
        String customPropertyValue = "customProperty_value";
        String customPropertyKey = "customProperty";
        values.put(customPropertyKey, Value.objectToValue(customPropertyValue));

        EvaluationContext evaluationContext = new ImmutableContext(values);
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