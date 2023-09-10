package dev.openfeature.contrib.providers.unleash;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.getunleash.util.UnleashConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UnleashProvider Integration test via live Unleash instance.
 * To trigger manually only.
 * To test it, set API_KEY and other values accordingly.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class UnleashProviderIntegrationTest {

    private static final String FLAG_NAME = "open-feature_flag1";
    public static final String API_KEY = null;
    public static final String API_URL = "https://app.unleash-hosted.com/demo/api/";
    public static final String APP_NAME = "open-feature";
    public static final String PROJECT_NAME = "open-feature";
    private UnleashProvider unleashProvider;
    private Client client;

    @BeforeAll
    void setUp() {
        if (API_KEY == null) {
            log.debug("init: tests disabled");
            return;
        }
        unleashProvider = buildUnleashProvider(true);
        OpenFeatureAPI.getInstance().setProviderAndWait("sync", unleashProvider);
        client = OpenFeatureAPI.getInstance().getClient("sync");
    }

    @AfterAll
    public void shutdown() {
        if (API_KEY == null) {
            log.debug("shutdown: tests disabled");
            return;
        }
        unleashProvider.shutdown();
    }

    private UnleashProvider buildUnleashProvider(boolean synchronousFetchOnInitialisation) {
        UnleashConfig.Builder unleashConfigBuilder =
            UnleashConfig.builder()
                .unleashAPI(API_URL)
                .appName(APP_NAME)
                .apiKey(API_KEY)
                .projectName(PROJECT_NAME)
                .synchronousFetchOnInitialisation(synchronousFetchOnInitialisation);

        UnleashOptions unleashOptions = UnleashOptions.builder()
            .unleashConfigBuilder(unleashConfigBuilder)
                .build();
        return new UnleashProvider(unleashOptions);
    }

    @Test
    void getBooleanEvaluation() {
        if (API_KEY == null) {
            log.debug("test disabled");
            return;
        }
        assertEquals(true, unleashProvider.getBooleanEvaluation(FLAG_NAME, false, new ImmutableContext()).getValue());
        assertEquals(true, client.getBooleanValue(FLAG_NAME, false));
        assertEquals(false, unleashProvider.getBooleanEvaluation("non-existing", false, new ImmutableContext()).getValue());
        assertEquals(false, client.getBooleanValue("non-existing", false));
    }

    @Test
    void getStringVariantEvaluation() {
        if (API_KEY == null) {
            log.debug("test disabled");
            return;
        }
        assertEquals("default_variant_value", unleashProvider.getStringEvaluation(FLAG_NAME, "",
                new ImmutableContext()).getValue());
        assertEquals("default_variant_value", client.getStringValue(FLAG_NAME, ""));
        assertEquals("fallback_str", unleashProvider.getStringEvaluation("non-existing",
                "fallback_str", new ImmutableContext()).getValue());
        assertEquals("fallback_str", client.getStringValue("non-existing", "fallback_str"));
    }

}