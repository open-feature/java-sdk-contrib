package dev.openfeature.contrib.providers.statsig;

import static dev.openfeature.contrib.providers.statsig.ContextTransformer.CONTEXT_APP_VERSION;
import static dev.openfeature.contrib.providers.statsig.ContextTransformer.CONTEXT_COUNTRY;
import static dev.openfeature.contrib.providers.statsig.ContextTransformer.CONTEXT_EMAIL;
import static dev.openfeature.contrib.providers.statsig.ContextTransformer.CONTEXT_IP;
import static dev.openfeature.contrib.providers.statsig.ContextTransformer.CONTEXT_LOCALE;
import static dev.openfeature.contrib.providers.statsig.ContextTransformer.CONTEXT_PRIVATE_ATTRIBUTES;
import static dev.openfeature.contrib.providers.statsig.ContextTransformer.CONTEXT_USER_AGENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.statsig.DynamicConfig;
import com.statsig.Layer;
import com.statsig.StatsigOptions;
import com.statsig.StatsigUser;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * StatsigProvider test, based on local config file evaluation. Configuration file test by statsig
 * tests.
 */
class StatsigProviderTest {

    private static final String FLAG_NAME = "enabledFeature";
    private static final String CONFIG_FLAG_NAME = "alias";
    private static final String LAYER_FLAG_NAME = "alias";
    private static final String CONFIG_FLAG_VALUE = "test";
    private static final String INT_FLAG_NAME = "revision";
    private static final String LAYER_INT_FLAG_NAME = "revision";
    private static final Integer INT_FLAG_VALUE = 5;
    private static final String DOUBLE_FLAG_NAME = "price";
    private static final String LAYER_DOUBLE_FLAG_NAME = "price";
    private static final Double DOUBLE_FLAG_VALUE = 3.14;
    private static final String USERS_FLAG_NAME = "userIdMatching";
    private static final String PROPERTIES_FLAG_NAME = "emailMatching";
    private static final String TARGETING_KEY = "user1";
    private static StatsigProvider statsigProvider;
    private static Client client;

    @SneakyThrows
    @BeforeAll
    static void setUp() {
        String sdkKey = "test";
        StatsigOptions statsigOptions = new StatsigOptions.Builder().build();
        StatsigProviderConfig statsigProviderConfig = StatsigProviderConfig.builder()
                .sdkKey(sdkKey)
                .options(statsigOptions)
                .build();

        statsigProvider = spy(new StatsigProvider(statsigProviderConfig));
        OpenFeatureAPI.getInstance().setProviderAndWait(statsigProvider);
        client = OpenFeatureAPI.getInstance().getClient();
        buildFlags();
    }

    @SneakyThrows
    private static void buildFlags() {
        statsigProvider.getStatsig().overrideGate(FLAG_NAME, true);
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("boolean", true);
        configMap.put("alias", "test");
        configMap.put("revision", INT_FLAG_VALUE);
        configMap.put("price", DOUBLE_FLAG_VALUE);
        statsigProvider.getStatsig().overrideDynamicConfig("product", configMap);
        statsigProvider.getStatsig().overrideLayer("product", configMap);

        ArrayList<Map<String, String>> secondaryExposures = new ArrayList<>();
        secondaryExposures.add(Collections.singletonMap("test-exposure", "test-exposure-value"));

        DynamicConfig dynamicConfig = mock(DynamicConfig.class);
        when(dynamicConfig.getName()).thenReturn("object-config-name");
        when(dynamicConfig.getValue()).thenReturn(Collections.singletonMap("value-key", "test-value"));
        when(dynamicConfig.getRuleID()).thenReturn("test-rule-id");

        doAnswer(invocation -> {
                    if ("object-config-name"
                            .equals(invocation
                                    .getArgument(1, StatsigProvider.FeatureConfig.class)
                                    .getName())) {
                        return dynamicConfig;
                    }
                    return invocation.callRealMethod();
                })
                .when(statsigProvider)
                .fetchDynamicConfig(any(), any());

        // Mock Layer
        Layer layer = mock(Layer.class);
        when(layer.getName()).thenReturn("layer-name");
        when(layer.getValue()).thenReturn(Collections.singletonMap("value-key", "test-value"));
        when(layer.getRuleID()).thenReturn("test-rule-id");

        doAnswer(invocation -> {
                    if ("layer-name"
                            .equals(invocation
                                    .getArgument(1, StatsigProvider.FeatureConfig.class)
                                    .getName())) {
                        return layer;
                    }
                    return invocation.callRealMethod();
                })
                .when(statsigProvider)
                .fetchLayer(any(), any());
    }

    @AfterAll
    static void shutdown() {
        statsigProvider.shutdown();
    }

    @Test
    void getBooleanEvaluation() {
        FlagEvaluationDetails<Boolean> flagEvaluationDetails =
                client.getBooleanDetails(FLAG_NAME, false, new ImmutableContext());
        assertEquals(false, flagEvaluationDetails.getValue());
        assertEquals("ERROR", flagEvaluationDetails.getReason());

        boolean res = statsigProvider
                .getStatsig()
                .checkGate(new StatsigUser.Builder().setUserID(TARGETING_KEY).build(), FLAG_NAME);

        System.out.println("Overridden flag evaluation: " + res);

        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        assertEquals(
                true,
                statsigProvider
                        .getBooleanEvaluation(FLAG_NAME, false, evaluationContext)
                        .getValue());
        assertEquals(true, client.getBooleanValue(FLAG_NAME, false, evaluationContext));
        assertEquals(
                false,
                statsigProvider
                        .getBooleanEvaluation("non-existing", false, evaluationContext)
                        .getValue());
        assertEquals(false, client.getBooleanValue("non-existing", false, evaluationContext));
        assertEquals(true, client.getBooleanValue("non-existing", true));

        MutableContext featureConfig = new MutableContext();
        featureConfig.add("type", "CONFIG");
        featureConfig.add("name", "product");
        evaluationContext.add("feature_config", featureConfig);
        assertEquals(
                true,
                statsigProvider
                        .getBooleanEvaluation("boolean", false, evaluationContext)
                        .getValue());
    }

    @Test
    void getStringEvaluation() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        MutableContext featureConfig = new MutableContext();
        featureConfig.add("type", "CONFIG");
        featureConfig.add("name", "product");
        evaluationContext.add("feature_config", featureConfig);
        assertEquals(
                CONFIG_FLAG_VALUE,
                statsigProvider
                        .getStringEvaluation(CONFIG_FLAG_NAME, "", evaluationContext)
                        .getValue());
        assertEquals(
                CONFIG_FLAG_VALUE,
                statsigProvider
                        .getStringEvaluation(LAYER_FLAG_NAME, "", evaluationContext)
                        .getValue());
        assertEquals("fallback_str", client.getStringValue("non-existing", "fallback_str"));
    }

    @Test
    void getObjectConfigEvaluation() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        MutableContext featureConfig = new MutableContext();
        featureConfig.add("type", "CONFIG");
        featureConfig.add("name", "object-config-name");
        evaluationContext.add("feature_config", featureConfig);
        Value objectEvaluation = statsigProvider
                .getObjectEvaluation("dummy", new Value("fallback"), evaluationContext)
                .getValue();

        String expectedObjectEvaluation =
                "{name=object-config-name, ruleID=test-rule-id, value={value-key=test-value}}";
        assertEquals(
                expectedObjectEvaluation,
                objectEvaluation.asStructure().asObjectMap().toString());
    }

    @Test
    void getObjectLayerEvaluation() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        MutableContext featureConfig = new MutableContext();
        featureConfig.add("type", "LAYER");
        featureConfig.add("name", "layer-name");
        evaluationContext.add("feature_config", featureConfig);
        Value objectEvaluation = statsigProvider
                .getObjectEvaluation("dummy", new Value("fallback"), evaluationContext)
                .getValue();

        String expectedObjectEvaluation =
                "{groupName=null, name=layer-name, allocatedExperiment=null, ruleID=test-rule-id, "
                        + "value={value-key=test-value}}";
        assertEquals(
                expectedObjectEvaluation,
                objectEvaluation.asStructure().asObjectMap().toString());
    }

    @Test
    void getIntegerEvaluation() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        MutableContext featureConfig = new MutableContext();
        featureConfig.add("type", "CONFIG");
        featureConfig.add("name", "product");
        evaluationContext.add("feature_config", featureConfig);
        assertEquals(
                INT_FLAG_VALUE,
                statsigProvider
                        .getIntegerEvaluation(INT_FLAG_NAME, 1, evaluationContext)
                        .getValue());
        assertEquals(
                INT_FLAG_VALUE,
                statsigProvider
                        .getIntegerEvaluation(LAYER_INT_FLAG_NAME, 1, evaluationContext)
                        .getValue());
        assertEquals(1, client.getIntegerValue("non-existing", 1));

        // non-number flag value
        assertEquals(1, client.getIntegerValue(CONFIG_FLAG_NAME, 1));
    }

    @Test
    void getDoubleEvaluation() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        MutableContext featureConfig = new MutableContext();
        featureConfig.add("type", "CONFIG");
        featureConfig.add("name", "product");
        evaluationContext.add("feature_config", featureConfig);
        assertEquals(
                DOUBLE_FLAG_VALUE,
                statsigProvider
                        .getDoubleEvaluation(DOUBLE_FLAG_NAME, 1.1, evaluationContext)
                        .getValue());
        assertEquals(
                DOUBLE_FLAG_VALUE,
                statsigProvider
                        .getDoubleEvaluation(LAYER_DOUBLE_FLAG_NAME, 1.1, evaluationContext)
                        .getValue());
        assertEquals(1.1, client.getDoubleValue("non-existing", 1.1));

        // non-number flag value
        assertEquals(1.1, client.getDoubleValue(CONFIG_FLAG_NAME, 1.1));
    }

    @Test
    void getBooleanEvaluationByUser() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(TARGETING_KEY);
        MutableContext featureConfig = new MutableContext();
        featureConfig.add("type", "CONFIG");
        featureConfig.add("name", "product");
        evaluationContext.add("feature_config", featureConfig);
        final String expectedTargetingKey = "test-id";
        evaluationContext.setTargetingKey(expectedTargetingKey);

        when(statsigProvider.getBooleanEvaluation(USERS_FLAG_NAME, false, evaluationContext))
                .thenAnswer(invocation -> {
                    if (!USERS_FLAG_NAME.equals(invocation.getArgument(0, String.class))) {
                        invocation.callRealMethod();
                    }
                    boolean evaluatedValue = invocation
                            .getArgument(2, MutableContext.class)
                            .getTargetingKey()
                            .equals(expectedTargetingKey);
                    return ProviderEvaluation.<Boolean>builder()
                            .value(evaluatedValue)
                            .build();
                });

        assertEquals(
                true,
                statsigProvider
                        .getBooleanEvaluation(USERS_FLAG_NAME, false, evaluationContext)
                        .getValue());
        evaluationContext.setTargetingKey("other-id");
        assertEquals(
                false,
                statsigProvider
                        .getBooleanEvaluation(USERS_FLAG_NAME, false, evaluationContext)
                        .getValue());
    }

    @Test
    void getBooleanEvaluationByProperties() {
        MutableContext evaluationContext = new MutableContext();
        MutableContext featureConfig = new MutableContext();
        featureConfig.add("type", "CONFIG");
        featureConfig.add("name", "product");
        evaluationContext.add("feature_config", featureConfig);
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
                    boolean evaluatedValue = invocation
                            .getArgument(2, MutableContext.class)
                            .getValue(CONTEXT_EMAIL)
                            .asString()
                            .equals(expectedEmail);
                    if (invocation
                            .getArgument(2, MutableContext.class)
                            .getValue(CONTEXT_PRIVATE_ATTRIBUTES)
                            .asStructure()
                            .getValue(CONTEXT_IP)
                            .asString()
                            .equals(expectedIp)) {
                        evaluatedValue = true;
                    }
                    return ProviderEvaluation.<Boolean>builder()
                            .value(evaluatedValue)
                            .build();
                });

        assertEquals(
                true,
                statsigProvider
                        .getBooleanEvaluation(PROPERTIES_FLAG_NAME, false, evaluationContext)
                        .getValue());
        evaluationContext.add(CONTEXT_EMAIL, "non-match@test.com");
        assertEquals(
                false,
                statsigProvider
                        .getBooleanEvaluation(PROPERTIES_FLAG_NAME, false, evaluationContext)
                        .getValue());

        privateAttributes.add(CONTEXT_IP, expectedIp);
        assertEquals(
                true,
                statsigProvider
                        .getBooleanEvaluation(PROPERTIES_FLAG_NAME, false, evaluationContext)
                        .getValue());
        privateAttributes.add(CONTEXT_IP, "1.2.3.5");
        assertEquals(
                false,
                statsigProvider
                        .getBooleanEvaluation(PROPERTIES_FLAG_NAME, false, evaluationContext)
                        .getValue());
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

        HashMap<String, String> customMap = new HashMap<>();
        customMap.put(customPropertyKey, customPropertyValue);
        StatsigUser expectedUser = new StatsigUser.Builder()
                .setUserID(evaluationContext.getTargetingKey())
                .setEmail(email)
                .setCountry(country)
                .setUserAgent(userAgent)
                .setIp(ip)
                .setAppVersion(appVersion)
                .setPrivateAttributes(Collections.singletonMap(CONTEXT_LOCALE, locale))
                .setCustomIDs(customMap)
                .build();
        StatsigUser transformedUser = ContextTransformer.transform(evaluationContext);

        assertEquals(expectedUser.getUserID(), transformedUser.getUserID());
        assertEquals(expectedUser.getEmail(), transformedUser.getEmail());
        assertEquals(expectedUser.getCountry(), transformedUser.getCountry());
        assertEquals(expectedUser.getUserAgent(), transformedUser.getUserAgent());
        assertEquals(expectedUser.getIp(), transformedUser.getIp());
        assertEquals(expectedUser.getAppVersion(), transformedUser.getAppVersion());
        assertEquals(expectedUser.getPrivateAttributes(), transformedUser.getPrivateAttributes());
        assertEquals(expectedUser.getCustomIDs(), transformedUser.getCustomIDs());
    }
}
