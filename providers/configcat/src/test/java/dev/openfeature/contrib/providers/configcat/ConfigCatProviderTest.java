package dev.openfeature.contrib.providers.configcat;

import com.configcat.OverrideBehaviour;
import com.configcat.OverrideDataSourceBuilder;
import com.configcat.User;
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

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ConfigCatProvider test, based on local config file evaluation.
 * Configuration file test by ConfigCat tests.
 */
class ConfigCatProviderTest {

    public static final String FLAG_NAME = "enabledFeature";
    public static final String VARIANT_FLAG_NAME = "stringSetting";
    public static final String VARIANT_FLAG_VALUE = "test";
    public static final String INT_FLAG_NAME = "intSetting";
    public static final Integer INT_FLAG_VALUE = 5;
    public static final String DOUBLE_FLAG_NAME = "doubleSetting";
    public static final Double DOUBLE_FLAG_VALUE = 3.14;
    public static final String USERS_FLAG_NAME = "disabledFeature";
    private static ConfigCatProvider configCatProvider;
    private static Client client;

    @BeforeAll
    static void setUp() {
        String sdkKey = "test";
        ConfigCatProviderConfig configCatProviderConfig = ConfigCatProviderConfig.builder().sdkKey(sdkKey)
            .options(options ->
                options.flagOverrides(
                    OverrideDataSourceBuilder.classPathResource("features.json"),
                    OverrideBehaviour.LOCAL_ONLY)).build();
        configCatProvider = new ConfigCatProvider(configCatProviderConfig);
        OpenFeatureAPI.getInstance().setProviderAndWait(configCatProvider);
        client = OpenFeatureAPI.getInstance().getClient();
    }

    @AfterAll
    static void shutdown() {
        configCatProvider.shutdown();
    }

    @Test
    void getBooleanEvaluation() {
        assertEquals(true, configCatProvider.getBooleanEvaluation(FLAG_NAME, false, new ImmutableContext()).getValue());
        assertEquals(true, client.getBooleanValue(FLAG_NAME, false));
        assertEquals(false, configCatProvider.getBooleanEvaluation("non-existing", false, new ImmutableContext()).getValue());
        assertEquals(false, client.getBooleanValue("non-existing", false));
    }

    @Test
    void getStringEvaluation() {
        assertEquals(VARIANT_FLAG_VALUE, configCatProvider.getStringEvaluation(VARIANT_FLAG_NAME, "",
            new ImmutableContext()).getValue());
        assertEquals(VARIANT_FLAG_VALUE, client.getStringValue(VARIANT_FLAG_NAME, ""));
        assertEquals("fallback_str", configCatProvider.getStringEvaluation("non-existing",
    "fallback_str", new ImmutableContext()).getValue());
        assertEquals("fallback_str", client.getStringValue("non-existing", "fallback_str"));
    }

    @Test
    void getObjectEvaluation() {
        assertEquals(VARIANT_FLAG_VALUE, configCatProvider.getStringEvaluation(VARIANT_FLAG_NAME, "",
                new ImmutableContext()).getValue());
        assertEquals(new Value(VARIANT_FLAG_VALUE), client.getObjectValue(VARIANT_FLAG_NAME, new Value("")));
        assertEquals(new Value("fallback_str"), configCatProvider.getObjectEvaluation("non-existing",
                new Value("fallback_str"), new ImmutableContext()).getValue());
        assertEquals(new Value("fallback_str"), client.getObjectValue("non-existing", new Value("fallback_str")));
    }

    @Test
    void getIntegerEvaluation() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.add("userId", "int");
        assertEquals(INT_FLAG_VALUE, configCatProvider.getIntegerEvaluation(INT_FLAG_NAME, 1,
            evaluationContext).getValue());
        assertEquals(INT_FLAG_VALUE, client.getIntegerValue(INT_FLAG_NAME, 1));
        assertEquals(1, client.getIntegerValue("non-existing", 1));

        // non-number flag value
        assertEquals(1, client.getIntegerValue(VARIANT_FLAG_NAME, 1));
    }

    @Test
    void getDoubleEvaluation() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.add("userId", "double");
        assertEquals(DOUBLE_FLAG_VALUE, configCatProvider.getDoubleEvaluation(DOUBLE_FLAG_NAME, 1.1,
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
        evaluationContext.add("Email", "a@b.com");
        evaluationContext.add("Country", "someCountry");
        assertEquals(true, configCatProvider.getBooleanEvaluation(USERS_FLAG_NAME, false, evaluationContext).getValue());
        assertEquals(true, client.getBooleanValue(USERS_FLAG_NAME, false, evaluationContext));
        evaluationContext.setTargetingKey("csp@notmatching.com");
        assertEquals(false, configCatProvider.getBooleanEvaluation(USERS_FLAG_NAME, false, evaluationContext).getValue());
        assertEquals(false, client.getBooleanValue(USERS_FLAG_NAME, false, evaluationContext));
    }

    @SneakyThrows
    @Test
    void shouldThrowIfNotInitialized() {
        ConfigCatProviderConfig configCatProviderConfig = ConfigCatProviderConfig.builder().sdkKey("test").build();
        ConfigCatProvider tempConfigCatProvider = new ConfigCatProvider(configCatProviderConfig);

        assertThrows(ProviderNotReadyError.class, ()-> tempConfigCatProvider.getBooleanEvaluation("fail_not_initialized", false, new ImmutableContext()));

        OpenFeatureAPI.getInstance().setProviderAndWait("tempConfigCatProvider", tempConfigCatProvider);

        assertThrows(GeneralError.class, ()-> tempConfigCatProvider.initialize(null));

        tempConfigCatProvider.shutdown();

        // ErrorCode.PROVIDER_NOT_READY should be returned when evaluated via the client
        assertThrows(ProviderNotReadyError.class, ()-> tempConfigCatProvider.getBooleanEvaluation("fail_not_initialized", false, new ImmutableContext()));
        assertThrows(ProviderNotReadyError.class, ()-> tempConfigCatProvider.getDoubleEvaluation("fail_not_initialized", 0.1, new ImmutableContext()));
        assertThrows(ProviderNotReadyError.class, ()-> tempConfigCatProvider.getIntegerEvaluation("fail_not_initialized", 3, new ImmutableContext()));
        assertThrows(ProviderNotReadyError.class, ()-> tempConfigCatProvider.getObjectEvaluation("fail_not_initialized", null, new ImmutableContext()));
        assertThrows(ProviderNotReadyError.class, ()-> tempConfigCatProvider.getStringEvaluation("fail_not_initialized", "", new ImmutableContext()));
    }

    @Test
    void eventsTest() {
        configCatProvider.emitProviderReady(ProviderEventDetails.builder().build());
        configCatProvider.emitProviderError(ProviderEventDetails.builder().build());
        configCatProvider.emitProviderConfigurationChanged(ProviderEventDetails.builder().build());
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
        User expectedUser = User.newBuilder().email(email).country(country).custom(customMap).build(
            evaluationContext.getTargetingKey());
        User transformedUser = ContextTransformer.transform(evaluationContext);

        // equals not implemented for User, using toString
        assertEquals(expectedUser.toString(), transformedUser.toString());
    }

}