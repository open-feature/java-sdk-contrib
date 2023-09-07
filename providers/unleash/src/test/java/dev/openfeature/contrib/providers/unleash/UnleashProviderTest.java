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
import io.getunleash.ActivationStrategy;
import io.getunleash.FeatureToggle;
import io.getunleash.UnleashContext;
import io.getunleash.UnleashContextProvider;
import io.getunleash.UnleashException;
import io.getunleash.event.EventDispatcher;
import io.getunleash.event.ToggleEvaluated;
import io.getunleash.event.UnleashEvent;
import io.getunleash.event.UnleashReady;
import io.getunleash.event.UnleashSubscriber;
import io.getunleash.metric.UnleashMetricService;
import io.getunleash.repository.FeatureRepository;
import io.getunleash.repository.FeatureToggleResponse;
import io.getunleash.strategy.DefaultStrategy;
import io.getunleash.strategy.Strategy;
import io.getunleash.strategy.UserWithIdStrategy;
import io.getunleash.util.UnleashConfig;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UnleashProvider Test.
 * Inspired by Unleash tests.
 */
class UnleashProviderTest {

    private static final String FLAG_NAME = "flagName";
    private FeatureRepository featureRepository;
    private UnleashContextProvider contextProvider;
    private EventDispatcher eventDispatcher;
    private UnleashMetricService metricService;
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

    private UnleashProvider buildUnleashProvider(boolean synchronousFetchOnInitialisation) {
        TestSubscriber testSubscriber = new TestSubscriber();
        UnleashConfig.Builder unleashConfigBuilder =
            UnleashConfig.builder()
                .unleashAPI("http://fakeAPI")
                .appName("fakeApp")
                .subscriber(testSubscriber)
                .synchronousFetchOnInitialisation(synchronousFetchOnInitialisation);
        featureRepository = mock(FeatureRepository.class);
        when(featureRepository.getToggle(FLAG_NAME))
            .thenReturn(
                new FeatureToggle(
                    FLAG_NAME, true, asList(new ActivationStrategy("default", null))));
        Map<String, Strategy> strategyMap = new HashMap<>();
        strategyMap.put("default", new DefaultStrategy());
        // Set up a toggleName using UserWithIdStrategy
        Map<String, String> params = new HashMap<>();
        UserWithIdStrategy userWithIdStrategy = new UserWithIdStrategy();
        strategyMap.put(userWithIdStrategy.getName(), userWithIdStrategy);
        contextProvider = mock(UnleashContextProvider.class);
        eventDispatcher = mock(EventDispatcher.class);
        metricService = mock(UnleashMetricService.class);
        when(contextProvider.getContext()).thenReturn(UnleashContext.builder().build());

        UnleashOptions unleashOptions = UnleashOptions.builder()
            .unleashConfigBuilder(unleashConfigBuilder)
                .featureRepository(featureRepository)
                .strategyMap(strategyMap)
                .contextProvider(contextProvider)
                .eventDispatcher(eventDispatcher)
                .metricService(metricService).build();
        return new UnleashProvider(unleashOptions);
    }

    @Test
    void getBooleanEvaluation() {
        assertEquals(true, unleashProvider.getBooleanEvaluation(FLAG_NAME, false, new ImmutableContext()).getValue());
        assertEquals(true, client.getBooleanValue(FLAG_NAME, false));
        assertEquals(false, unleashProvider.getBooleanEvaluation("non-existing", false, new ImmutableContext()).getValue());
        assertEquals(false, client.getBooleanValue("non-existing", false));
    }

    @Test
    void getBooleanEvaluationByUser() {

        // Set up a toggleName using UserWithIdStrategy
        Map<String, String> params = new HashMap<>();
        params.put("userIds", "1");
        ActivationStrategy strategy = new ActivationStrategy("userWithId", params);
        String flagName = "testByUserId";
        FeatureToggle featureToggle = new FeatureToggle(flagName, true, asList(strategy));
        when(featureRepository.getToggle(flagName)).thenReturn(featureToggle);

        UnleashContext unleashContext = UnleashContext.builder().userId("1").build();
        EvaluationContext evaluationContext = UnleashProvider.transform(unleashContext);
        assertEquals(true, unleashProvider.getBooleanEvaluation(flagName, false, evaluationContext).getValue());
        assertEquals(true, client.getBooleanValue(flagName, false, evaluationContext));
        unleashContext = UnleashContext.builder().userId("2").build();
        evaluationContext = UnleashProvider.transform(unleashContext);
        assertEquals(false, unleashProvider.getBooleanEvaluation(flagName, false, evaluationContext).getValue());
        assertEquals(false, client.getBooleanValue(flagName, false, evaluationContext));
    }

    @Test
    void typeMismatch() {
        assertThrows(TypeMismatchError.class, () -> {
            unleashProvider.getStringEvaluation("test", "default_value", new ImmutableContext());
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
        UnleashContext transformedUnleashContext = UnleashProvider.transform(evaluationContext);
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