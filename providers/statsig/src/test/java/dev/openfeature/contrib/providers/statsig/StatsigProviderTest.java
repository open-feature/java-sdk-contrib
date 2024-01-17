package dev.openfeature.contrib.providers.statsig;

import com.statsig.OverrideBehaviour;
import com.statsig.OverrideDataSourceBuilder;
import com.statsig.User;
import com.statsig.sdk.Statsig;
import com.statsig.sdk.StatsigOptions;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * StatsigProvider test, based on local config file evaluation.
 * Configuration file test by statsig tests.
 */
class StatsigProviderTest {

    public static final String FLAG_NAME = "enabledFeature";
    public static final String CONFIG_FLAG_NAME = "config.product.name";
    public static final String LAYER_FLAG_NAME = "layer.product.name";
    public static final String VARIANT_FLAG_VALUE = "test";
    public static final String INT_FLAG_NAME = "intSetting";
    public static final Integer INT_FLAG_VALUE = 5;
    public static final String DOUBLE_FLAG_NAME = "doubleSetting";
    public static final Double DOUBLE_FLAG_VALUE = 3.14;
    public static final String USERS_FLAG_NAME = "userIdMatching";
    public static final String EMAIL_FLAG_NAME = "emailMatching";
    public static final String COUNTRY_FLAG_NAME = "countryMatching";
    private static StatsigProvider statsigProvider;
    private static Client client;

    @BeforeAll
    static void setUp() {
        String sdkKey = "test";
        StatsigOptions statsigOptions = new StatsigOptions();
        statsigOptions.setLocalMode(true);
        StatsigProviderConfig statsigProviderConfig = StatsigProviderConfig.builder().sdkKey(sdkKey)
            .options(statsigOptions).build();
        statsigProvider = new StatsigProvider(statsigProviderConfig);
        OpenFeatureAPI.getInstance().setProviderAndWait(statsigProvider);
        client = OpenFeatureAPI.getInstance().getClient();
        buildFlags();
    }

    private static void buildFlags() {
        Statsig.overrideGate(FLAG_NAME, true);
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("name", "test");
        configMap.put("version", "1");
        configMap.put("price", "1.2");
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
        assertEquals(VARIANT_FLAG_VALUE, statsigProvider.getStringEvaluation(CONFIG_FLAG_NAME, "",
            new ImmutableContext()).getValue());
        assertEquals(VARIANT_FLAG_VALUE, statsigProvider.getStringEvaluation(LAYER_FLAG_NAME, "",
                new ImmutableContext()).getValue());
        assertEquals(VARIANT_FLAG_VALUE, client.getStringValue(CONFIG_FLAG_NAME, ""));
        assertEquals("fallback_str", statsigProvider.getStringEvaluation("non-existing",
    "fallback_str", new ImmutableContext()).getValue());
        assertEquals("fallback_str", client.getStringValue("non-existing", "fallback_str"));
    }

    @Test
    void getObjectEvaluation() {
        assertEquals(VARIANT_FLAG_VALUE, statsigProvider.getStringEvaluation(VARIANT_FLAG_NAME, "",
                new ImmutableContext()).getValue());
        assertEquals(new Value(VARIANT_FLAG_VALUE), client.getObjectValue(VARIANT_FLAG_NAME, new Value("")));
        assertEquals(new Value("fallback_str"), statsigProvider.getObjectEvaluation("non-existing",
                new Value("fallback_str"), new ImmutableContext()).getValue());
        assertEquals(new Value("fallback_str"), client.getObjectValue("non-existing", new Value("fallback_str")));
    }

    @Test
    void getIntegerEvaluation() {
        MutableContext evaluationContext = new MutableContext();
        assertEquals(INT_FLAG_VALUE, statsigProvider.getIntegerEvaluation(INT_FLAG_NAME, 1,
            evaluationContext).getValue());
        assertEquals(INT_FLAG_VALUE, client.getIntegerValue(INT_FLAG_NAME, 1));
        assertEquals(1, client.getIntegerValue("non-existing", 1));

        // non-number flag value
        assertEquals(1, client.getIntegerValue(VARIANT_FLAG_NAME, 1));
    }

    @Test
    void getDoubleEvaluation() {
        MutableContext evaluationContext = new MutableContext();
        assertEquals(DOUBLE_FLAG_VALUE, statsigProvider.getDoubleEvaluation(DOUBLE_FLAG_NAME, 1.1,
            evaluationContext).getValue());
        assertEquals(DOUBLE_FLAG_VALUE, client.getDoubleValue(DOUBLE_FLAG_NAME, 1.1));
        assertEquals(1.1, client.getDoubleValue("non-existing", 1.1));

        // non-number flag value
        assertEquals(1.1, client.getDoubleValue(VARIANT_FLAG_NAME, 1.1));
    }

    @Test
    void getBooleanEvaluationByUser() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey("csp@matching.com");
        assertEquals(true, statsigProvider.getBooleanEvaluation(USERS_FLAG_NAME, false, evaluationContext).getValue());
        assertEquals(true, client.getBooleanValue(USERS_FLAG_NAME, false, evaluationContext));
        evaluationContext.setTargetingKey("csp@notmatching.com");
        assertEquals(false, statsigProvider.getBooleanEvaluation(USERS_FLAG_NAME, false, evaluationContext).getValue());
        assertEquals(false, client.getBooleanValue(USERS_FLAG_NAME, false, evaluationContext));
    }

    @Test
    void getBooleanEvaluationByEmail() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey("csp@matching.com");
        evaluationContext.add("Email", "a@matching.com");
        assertEquals(true, statsigProvider.getBooleanEvaluation(EMAIL_FLAG_NAME, false, evaluationContext).getValue());
        assertEquals(true, client.getBooleanValue(EMAIL_FLAG_NAME, false, evaluationContext));
        evaluationContext.add("Email", "a@matchingnot.com");
        assertEquals(false, statsigProvider.getBooleanEvaluation(EMAIL_FLAG_NAME, false, evaluationContext).getValue());
        assertEquals(false, client.getBooleanValue(EMAIL_FLAG_NAME, false, evaluationContext));
    }

    @Test
    void getBooleanEvaluationByCountry() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey("csp@matching.com");
        evaluationContext.add("Country", "country1");
        assertEquals(true, statsigProvider.getBooleanEvaluation(COUNTRY_FLAG_NAME, false, evaluationContext).getValue());
        assertEquals(true, client.getBooleanValue(COUNTRY_FLAG_NAME, false, evaluationContext));
        evaluationContext.add("Country", "country2");
        assertEquals(false, statsigProvider.getBooleanEvaluation(COUNTRY_FLAG_NAME, false, evaluationContext).getValue());
        assertEquals(false, client.getBooleanValue(COUNTRY_FLAG_NAME, false, evaluationContext));
    }

    @Test
    void getIntEvaluationByUser() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey("csp@matching.com");
        assertEquals(123, statsigProvider.getIntegerEvaluation(USERS_FLAG_NAME + "Int", 111, evaluationContext).getValue());
        assertEquals(123, client.getIntegerValue(USERS_FLAG_NAME + "Int", 111, evaluationContext));
        evaluationContext.setTargetingKey("csp@notmatching.com");
        assertEquals(111, statsigProvider.getIntegerEvaluation(USERS_FLAG_NAME + "Int", 222, evaluationContext).getValue());
        assertEquals(111, client.getIntegerValue(USERS_FLAG_NAME + "Int", 222, evaluationContext));
    }

    @Test
    void getDoubleEvaluationByUser() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey("csp@matching.com");
        assertEquals(1.23, statsigProvider.getDoubleEvaluation(USERS_FLAG_NAME + "Double", 1.11, evaluationContext).getValue());
        assertEquals(1.23, client.getDoubleValue(USERS_FLAG_NAME + "Double", 1.11, evaluationContext));
        evaluationContext.setTargetingKey("csp@matchingnot.com");
        assertEquals(0.1, statsigProvider.getDoubleEvaluation(USERS_FLAG_NAME + "Double", 1.11, evaluationContext).getValue());
        assertEquals(0.1, client.getDoubleValue(USERS_FLAG_NAME + "Double", 1.11, evaluationContext));
    }

    @Test
    void getStringEvaluationByUser() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey("csp@matching.com");
        assertEquals("expected", statsigProvider.getStringEvaluation(USERS_FLAG_NAME + "Str", "111", evaluationContext).getValue());
        assertEquals("expected", client.getStringValue(USERS_FLAG_NAME + "Str", "111", evaluationContext));
        evaluationContext.setTargetingKey("csp@notmatching.com");
        assertEquals("fallback", statsigProvider.getStringEvaluation(USERS_FLAG_NAME + "Str", "111", evaluationContext).getValue());
        assertEquals("fallback", client.getStringValue(USERS_FLAG_NAME + "Str", "111", evaluationContext));
    }

    @SneakyThrows
    @Test
    void shouldThrowIfNotInitialized() {
        statsigProviderConfig statsigProviderConfig = statsigProviderConfig.builder().sdkKey("test").build();
        statsigProvider tempstatsigProvider = new statsigProvider(statsigProviderConfig);

        assertThrows(ProviderNotReadyError.class, ()-> tempstatsigProvider.getBooleanEvaluation("fail_not_initialized", false, new ImmutableContext()));

        OpenFeatureAPI.getInstance().setProviderAndWait("tempstatsigProvider", tempstatsigProvider);

        assertThrows(GeneralError.class, ()-> tempstatsigProvider.initialize(null));

        tempstatsigProvider.shutdown();

        assertThrows(ProviderNotReadyError.class, ()-> tempstatsigProvider.getBooleanEvaluation("fail_not_initialized", false, new ImmutableContext()));
        assertThrows(ProviderNotReadyError.class, ()-> tempstatsigProvider.getDoubleEvaluation("fail_not_initialized", 0.1, new ImmutableContext()));
        assertThrows(ProviderNotReadyError.class, ()-> tempstatsigProvider.getIntegerEvaluation("fail_not_initialized", 3, new ImmutableContext()));
        assertThrows(ProviderNotReadyError.class, ()-> tempstatsigProvider.getObjectEvaluation("fail_not_initialized", null, new ImmutableContext()));
        assertThrows(ProviderNotReadyError.class, ()-> tempstatsigProvider.getStringEvaluation("fail_not_initialized", "", new ImmutableContext()));
    }

    @Test
    void eventsTest() {
        statsigProvider.emitProviderReady(ProviderEventDetails.builder().build());
        statsigProvider.emitProviderError(ProviderEventDetails.builder().build());
        statsigProvider.emitProviderConfigurationChanged(ProviderEventDetails.builder().build());
        assertDoesNotThrow(() -> {statsigProvider.getState();});
    }

    @SneakyThrows
    @Test
    void contextTransformTest() {
        String userId = "a";
        String email = "a@a.com";
        String country = "someCountry";
        String customPropertyValue = "customProperty_value";
        String customPropertyKey = "customProperty";

        MutableContext evaluationContext = new MutableContext();
        evaluationContext.setTargetingKey(userId);
        evaluationContext.add("Country", country);
        evaluationContext.add("Email", email);
        evaluationContext.add(customPropertyKey, customPropertyValue);

        HashMap<String, String > customMap = new HashMap<>();
        customMap.put(customPropertyKey, customPropertyValue);
        User expectedUser = User.newBuilder().email(email).country(country).custom(customMap).build(userId);
        User transformedUser = ContextTransformer.transform(evaluationContext);

        // equals not implemented for User, using toString
        assertEquals(expectedUser.toString(), transformedUser.toString());
    }

}