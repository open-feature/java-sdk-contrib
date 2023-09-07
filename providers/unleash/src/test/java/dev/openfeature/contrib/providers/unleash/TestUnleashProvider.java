package dev.openfeature.contrib.providers.unleash;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderState;
import io.getunleash.ActivationStrategy;
import io.getunleash.DefaultUnleash;
import io.getunleash.FeatureToggle;
import io.getunleash.Unleash;
import io.getunleash.UnleashContextProvider;
import io.getunleash.event.EventDispatcher;
import io.getunleash.metric.UnleashMetricService;
import io.getunleash.repository.FeatureRepository;
import io.getunleash.strategy.DefaultStrategy;
import io.getunleash.strategy.Strategy;
import io.getunleash.strategy.UserWithIdStrategy;
import io.getunleash.util.UnleashConfig;
import io.getunleash.variant.Payload;
import io.getunleash.variant.VariantDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static dev.openfeature.contrib.providers.unleash.UnleashProviderTest.FLAG_NAME;
import static dev.openfeature.contrib.providers.unleash.UnleashProviderTest.VARIANT_1;
import static dev.openfeature.contrib.providers.unleash.UnleashProviderTest.VARIANT_1_VALUE;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
public class TestUnleashProvider extends UnleashProvider {
    /**
     * Constructor.
     *
     * @param unleashOptions UnleashOptions
     */
    public TestUnleashProvider(UnleashOptions unleashOptions) {
        super(unleashOptions);
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) {
        UnleashSubscriberWrapper unleashSubscriberWrapper = new UnleashSubscriberWrapper(
            getUnleashOptions().getUnleashConfigBuilder().build().getSubscriber(), this);
        getUnleashOptions().getUnleashConfigBuilder().subscriber(unleashSubscriberWrapper);
        UnleashConfig unleashConfig = getUnleashOptions().getUnleashConfigBuilder().build();

        UnleashContextProvider contextProvider = mock(UnleashContextProvider.class);
        EventDispatcher eventDispatcher = mock(EventDispatcher.class);
        UnleashMetricService metricService = mock(UnleashMetricService.class);
        FeatureRepository featureRepository = mock(FeatureRepository.class);
        VariantDefinition v1 =
            new VariantDefinition(
                VARIANT_1, 100, new Payload("string", VARIANT_1_VALUE), Collections.emptyList());
        when(featureRepository.getToggle(FLAG_NAME))
            .thenReturn(
                new FeatureToggle(
                    FLAG_NAME, true, asList(new ActivationStrategy("default", null)), Arrays.asList(v1)));

        Map<String, String> params = new HashMap<>();
        params.put("userIds", "1");
        ActivationStrategy strategy = new ActivationStrategy("userWithId", params);
        String flagName = "testByUserId";
        FeatureToggle featureToggle = new FeatureToggle(flagName, true, asList(strategy));
        when(featureRepository.getToggle(flagName)).thenReturn(featureToggle);

        Map<String, Strategy> strategyMap = new HashMap<>();
        strategyMap.put("default", new DefaultStrategy());
        // Set up a toggleName using UserWithIdStrategy
        UserWithIdStrategy userWithIdStrategy = new UserWithIdStrategy();
        strategyMap.put(userWithIdStrategy.getName(), userWithIdStrategy);

        Unleash unleash = new DefaultUnleash(unleashConfig,
            featureRepository,
            strategyMap,
            contextProvider,
            eventDispatcher,
            metricService,
            false);
        setUnleash(unleash);

        // else, state will be changed via UnleashSubscriberWrapper events
        if (unleashConfig.isSynchronousFetchOnInitialisation()) {
            setState(ProviderState.READY);
        } else {
            log.info("ready state will be changed via UnleashSubscriberWrapper events");
        }

        log.info("finished initializing provider, state: {}", getState());
    }
}
