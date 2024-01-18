package dev.openfeature.contrib.providers.statsig;

import com.statsig.sdk.Statsig;
import com.statsig.sdk.StatsigOptions;
import com.statsig.sdk.StatsigUser;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static dev.openfeature.contrib.providers.statsig.ContextTransformer.CONTEXT_APP_VERSION;
import static dev.openfeature.contrib.providers.statsig.ContextTransformer.CONTEXT_COUNTRY;
import static dev.openfeature.contrib.providers.statsig.ContextTransformer.CONTEXT_EMAIL;
import static dev.openfeature.contrib.providers.statsig.ContextTransformer.CONTEXT_IP;
import static dev.openfeature.contrib.providers.statsig.ContextTransformer.CONTEXT_LOCALE;
import static dev.openfeature.contrib.providers.statsig.ContextTransformer.CONTEXT_PRIVATE_ATTRIBUTES;
import static dev.openfeature.contrib.providers.statsig.ContextTransformer.CONTEXT_USER_AGENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * StatsigProvider test, based on local config file evaluation.
 * Configuration file test by statsig tests.
 */
class StatsigProviderTest {

    public static final String FLAG_NAME = "enabledFeature";
    public static final String CONFIG_FLAG_NAME = "config.product.name";
    public static final String LAYER_FLAG_NAME = "layer.product.name";
    public static final String CONFIG_FLAG_VALUE = "test";
    public static final String INT_FLAG_NAME = "config.product.revision";
    public static final String LAYER_INT_FLAG_NAME = "layer.product.revision";
    public static final Integer INT_FLAG_VALUE = 5;
    public static final String DOUBLE_FLAG_NAME = "config.product.price";
    public static final String LAYER_DOUBLE_FLAG_NAME = "layer.product.price";
    public static final Double DOUBLE_FLAG_VALUE = 3.14;
    public static final String USERS_FLAG_NAME = "userIdMatching";
    public static final String PROPERTIES_FLAG_NAME = "emailMatching";
    private static StatsigProvider statsigProvider;
    private static Client client;

    @BeforeAll
    static void setUp() {
        String sdkKey = "test";
        StatsigOptions statsigOptions = new StatsigOptions();
        statsigOptions.setLocalMode(true);
        StatsigProviderConfig statsigProviderConfig = StatsigProviderConfig.builder().sdkKey(sdkKey)
            .options(statsigOptions).build();
        statsigProvider = spy(new StatsigProvider(statsigProviderConfig));
        OpenFeatureAPI.getInstance().setProviderAndWait(statsigProvider);
        client = OpenFeatureAPI.getInstance().getClient();
        buildFlags();
    }

    private static void buildFlags() {
        Statsig.overrideGate(FLAG_NAME, true);
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("name", "test");
        configMap.put("revision", INT_FLAG_VALUE);
        configMap.put("price", DOUBLE_FLAG_VALUE);
        Statsig.overrideConfig("product", configMap);
        Statsig.overrideLayer("product", configMap);
    }

    @AfterAll
    static void shutdown() {
        statsigProvider.shutdown();
    }

    @Test
    void getBooleanEvaluation() {
        assertEquals(true, statsigProvider.getBooleanEvaluation(FLAG_NAME, false, new ImmutableContext()).getValue());
        assertEquals(true, client.getBooleanValue(FLAG_NAME, false));
        assertEquals(false, statsigProvider.getBooleanEvaluation("non-existing", false, new ImmutableContext()).getValue());
        assertEquals(false, client.getBooleanValue("non-existing", false));
    }

    @Test
    void getStringEvaluation() {
        assertEquals(CONFIG_FLAG_VALUE, statsigProvider.getStringEvaluation(CONFIG_FLAG_NAME, "",
            new ImmutableContext()).getValue());
        assertEquals(CONFIG_FLAG_VALUE, statsigProvider.getStringEvaluation(LAYER_FLAG_NAME, "",
            new ImmutableContext()).getValue());
        assertEquals(CONFIG_FLAG_VALUE, client.getStringValue(CONFIG_FLAG_NAME, ""));
        assertThrows(GeneralError.class, () -> statsigProvider.getStringEvaluation("non-existing",
    "fallback_str", new ImmutableContext()).getValue());
        assertEquals("fallback_str", client.getStringValue("non-existing", "fallback_str"));
    }

    @Test
    void getObjectEvaluation() {
        assertEquals(CONFIG_FLAG_VALUE, statsigProvider.getStringEvaluation(CONFIG_FLAG_NAME, "",
            new ImmutableContext()).getValue());
        assertEquals(CONFIG_FLAG_VALUE, statsigProvider.getStringEvaluation(LAYER_FLAG_NAME, "",
            new ImmutableContext()).getValue());
        assertEquals(new Value(CONFIG_FLAG_VALUE), client.getObjectValue(CONFIG_FLAG_NAME, new Value("")));
        assertThrows(GeneralError.class, () -> statsigProvider.getObjectEvaluation("non-existing",
            new Value("fallback_str"), new ImmutableContext()).getValue());
        assertEquals(new Value("fallback_str"), client.getObjectValue("non-existing", new Value("fallback_str")));
    }

    @Test
    void getIntegerEvaluation() {
        MutableContext evaluationContext = new MutableContext();
        assertEquals(INT_FLAG_VALUE, statsigProvider.getIntegerEvaluation(INT_FLAG_NAME, 1,
            evaluationContext).getValue());
        assertEquals(INT_FLAG_VALUE, statsigProvider.getIntegerEvaluation(LAYER_INT_FLAG_NAME, 1,
            evaluationContext).getValue());
        assertEquals(INT_FLAG_VALUE, client.getIntegerValue(INT_FLAG_NAME, 1));
        assertEquals(1, client.getIntegerValue("non-existing", 1));

        // non-number flag value
        assertEquals(1, client.getIntegerValue(CONFIG_FLAG_NAME, 1));
    }

    @Test
    void getDoubleEvaluation() {
        MutableContext evaluationContext = new MutableContext();
        assertEquals(DOUBLE_FLAG_VALUE, statsigProvider.getDoubleEvaluation(DOUBLE_FLAG_NAME, 1.1,
            evaluationContext).getValue());
        assertEquals(DOUBLE_FLAG_VALUE, statsigProvider.getDoubleEvaluation(LAYER_DOUBLE_FLAG_NAME, 1.1,
            evaluationContext).getValue());
        assertEquals(DOUBLE_FLAG_VALUE, client.getDoubleValue(DOUBLE_FLAG_NAME, 1.1));
        assertEquals(1.1, client.getDoubleValue("non-existing", 1.1));

        // non-number flag value
        assertEquals(1.1, client.getDoubleValue(CONFIG_FLAG_NAME, 1.1));
    }

    @Test
    void getBooleanEvaluationByUser() {
        MutableContext evaluationContext = new MutableContext();
        final String expectedTargetingKey = "test-id";
        evaluationContext.setTargetingKey(expectedTargetingKey);

        when(statsigProvider.getBooleanEvaluation(USERS_FLAG_NAME, false, evaluationContext))
            .thenAnswer(invocation -> {
                if (!USERS_FLAG_NAME.equals(invocation.getArgument(0, String.class))) {
                    invocation.callRealMethod();
                }
                boolean evaluatedValue = invocation.getArgument(2, MutableContext.class).getTargetingKey().equals(expectedTargetingKey);
                return ProviderEvaluation.<Boolean>builder()
                    .value(evaluatedValue)
                    .build();
                }
            );

        assertEquals(true, statsigProvider.getBooleanEvaluation(USERS_FLAG_NAME, false, evaluationContext).getValue());
        evaluationContext.setTargetingKey("other-id");
        assertEquals(false, statsigProvider.getBooleanEvaluation(USERS_FLAG_NAME, false, evaluationContext).getValue());
    }

    @Test
    void getBooleanEvaluationByProperties() {
        MutableContext evaluationContext = new MutableContext();
        final String expectedTargetingKey = "test-id";
        final String expectedEmail = "match@test.com";
        final String expectedIp = "1.2.3.4";
        evaluationContext.setTargetingKey(expectedTargetingKey);
        evaluationContext.add(CONTEXT_EMAIL, expectedEmail);
        evaluationContext.add(CONTEXT_LOCALE, "test-locale");
        MutableContext privateAttributes = new MutableContext();
        privateAttributes.add(CONTEXT_IP, "1.2.3.5");
        privateAttributes.add("custom-private", "test-custom");
        evaluationContext.add(CONTEXT_PRIVATE_ATTRIBUTES, privateAttributes);

        when(statsigProvider.getBooleanEvaluation(PROPERTIES_FLAG_NAME, false, evaluationContext))
            .thenAnswer(invocation -> {
                if (!PROPERTIES_FLAG_NAME.equals(invocation.getArgument(0, String.class))) {
                    invocation.callRealMethod();
                }
                boolean evaluatedValue = invocation.getArgument(2, MutableContext.class).getValue(CONTEXT_EMAIL).asString().equals(expectedEmail);
                if (invocation.getArgument(2, MutableContext.class).getValue(CONTEXT_PRIVATE_ATTRIBUTES).asStructure().getValue(CONTEXT_IP).asString().equals(expectedIp)) {
                    evaluatedValue = true;
                }
                return ProviderEvaluation.<Boolean>builder()
                    .value(evaluatedValue)
                    .build();
                }
            );

        assertEquals(true, statsigProvider.getBooleanEvaluation(PROPERTIES_FLAG_NAME, false, evaluationContext).getValue());
        evaluationContext.add(CONTEXT_EMAIL, "non-match@test.com");
        assertEquals(false, statsigProvider.getBooleanEvaluation(PROPERTIES_FLAG_NAME, false, evaluationContext).getValue());

        privateAttributes.add(CONTEXT_IP, expectedIp);
        assertEquals(true, statsigProvider.getBooleanEvaluation(PROPERTIES_FLAG_NAME, false, evaluationContext).getValue());
        privateAttributes.add(CONTEXT_IP, "1.2.3.5");
        assertEquals(false, statsigProvider.getBooleanEvaluation(PROPERTIES_FLAG_NAME, false, evaluationContext).getValue());
    }

    @SneakyThrows
    @Test
    void shouldThrowIfNotInitialized() {
        StatsigProviderConfig statsigProviderConfig = StatsigProviderConfig.builder().sdkKey("test").build();
        StatsigProvider tempstatsigProvider = new StatsigProvider(statsigProviderConfig);

        assertThrows(ProviderNotReadyError.class, ()-> tempstatsigProvider.getBooleanEvaluation("fail_not_initialized", false, new ImmutableContext()));

        OpenFeatureAPI.getInstance().setProviderAndWait("tempstatsigProvider", tempstatsigProvider);

        assertThrows(GeneralError.class, ()-> tempstatsigProvider.initialize(null));
    }

    @SneakyThrows
    @Test
    void contextTransformTest() {
        String userId = "a";
        String email = "a@a.com";
        String country = "someCountry";
        String userAgent = "userAgent1";
        String ip = "1.2.3.4";
        String appVersion = "appVersion1";
        String locale = "locale1";
        String customPropertyValue = "customProperty_value";
        String customPropertyKey = "customProperty";

        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(userId);
        evaluationContext.add(CONTEXT_COUNTRY, country);
        evaluationContext.add(CONTEXT_EMAIL, email);
        evaluationContext.add(CONTEXT_USER_AGENT, userAgent);
        evaluationContext.add(CONTEXT_IP, ip);
        evaluationContext.add(CONTEXT_APP_VERSION, appVersion);

        MutableContext privateAttributes = new MutableContext();
        privateAttributes.add(CONTEXT_LOCALE, locale);
        evaluationContext.add(CONTEXT_PRIVATE_ATTRIBUTES, privateAttributes);

        evaluationContext.add(customPropertyKey, customPropertyValue);

        HashMap<String, String > customMap = new HashMap<>();
        customMap.put(customPropertyKey, customPropertyValue);
        StatsigUser expectedUser = new StatsigUser(evaluationContext.getTargetingKey());
        expectedUser.setEmail(email);
        expectedUser.setCountry(country);
        expectedUser.setUserAgent(userAgent);
        expectedUser.setIp(ip);
        expectedUser.setAppVersion(appVersion);
        Map<String, String> privateAttributesMap = new HashMap<>();
        privateAttributesMap.put(CONTEXT_LOCALE, locale);
        expectedUser.setPrivateAttributes(privateAttributesMap);
        expectedUser.setCustomIDs(customMap);
        StatsigUser transformedUser = ContextTransformer.transform(evaluationContext);

        // equals not implemented for User, using toString
        assertEquals(expectedUser.toString(), transformedUser.toString());
    }

}