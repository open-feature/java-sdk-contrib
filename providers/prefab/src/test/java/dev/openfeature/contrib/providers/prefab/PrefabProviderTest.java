package dev.openfeature.contrib.providers.prefab;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cloud.prefab.client.Options;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.io.File;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PrefabProvider test, based local default config file.
 */
@Slf4j
class PrefabProviderTest {

    public static final String FLAG_NAME = "sample_bool";
    public static final String VARIANT_FLAG_NAME = "sample";
    public static final String VARIANT_FLAG_VALUE = "test sample value";
    public static final String INT_FLAG_NAME = "sample_int";
    public static final Integer INT_FLAG_VALUE = 123;
    public static final String DOUBLE_FLAG_NAME = "sample_double";
    public static final Double DOUBLE_FLAG_VALUE = 12.12;
    public static final String USERS_FLAG_NAME = "test1";
    private static PrefabProvider prefabProvider;
    private static Client client;

    @BeforeAll
    static void setUp() {
        File localDataFile = new File("src/test/resources/features.json");
        Options options = new Options()
                .setPrefabDatasource(Options.Datasources.ALL)
                .setLocalDatafile(localDataFile.toString())
                .setInitializationTimeoutSec(10);
        PrefabProviderConfig prefabProviderConfig =
                PrefabProviderConfig.builder().options(options).build();
        prefabProvider = new PrefabProvider(prefabProviderConfig);
        OpenFeatureAPI.getInstance().setProviderAndWait(prefabProvider);
        client = OpenFeatureAPI.getInstance().getClient();
    }

    @AfterAll
    static void shutdown() {
        prefabProvider.shutdown();
    }

    @Test
    void getBooleanEvaluation() {
        assertEquals(
                true,
                prefabProvider
                        .getBooleanEvaluation(FLAG_NAME, false, new ImmutableContext())
                        .getValue());
        assertEquals(true, client.getBooleanValue(FLAG_NAME, false));
        assertEquals(
                false,
                prefabProvider
                        .getBooleanEvaluation("non-existing", false, new ImmutableContext())
                        .getValue());
        assertEquals(false, client.getBooleanValue("non-existing", false));
    }

    @Test
    void getStringEvaluation() {
        assertEquals(
                VARIANT_FLAG_VALUE,
                prefabProvider
                        .getStringEvaluation(VARIANT_FLAG_NAME, "", new ImmutableContext())
                        .getValue());
        assertEquals(VARIANT_FLAG_VALUE, client.getStringValue(VARIANT_FLAG_NAME, ""));
        assertEquals(
                "fallback_str",
                prefabProvider
                        .getStringEvaluation("non-existing", "fallback_str", new ImmutableContext())
                        .getValue());
        assertEquals("fallback_str", client.getStringValue("non-existing", "fallback_str"));
    }

    @Test
    void getObjectEvaluation() {
        assertEquals(
                VARIANT_FLAG_VALUE,
                prefabProvider
                        .getStringEvaluation(VARIANT_FLAG_NAME, "", new ImmutableContext())
                        .getValue());
        assertEquals(new Value(VARIANT_FLAG_VALUE), client.getObjectValue(VARIANT_FLAG_NAME, new Value("")));
        assertEquals(
                new Value("fallback_str"),
                prefabProvider
                        .getObjectEvaluation("non-existing", new Value("fallback_str"), new ImmutableContext())
                        .getValue());
        assertEquals(new Value("fallback_str"), client.getObjectValue("non-existing", new Value("fallback_str")));
    }

    @Test
    void getIntegerEvaluation() {
        MutableContext evaluationContext = new MutableContext();
        assertEquals(
                INT_FLAG_VALUE,
                prefabProvider
                        .getIntegerEvaluation(INT_FLAG_NAME, 1, evaluationContext)
                        .getValue());
        assertEquals(INT_FLAG_VALUE, client.getIntegerValue(INT_FLAG_NAME, 1));
        assertEquals(1, client.getIntegerValue("non-existing", 1));

        // non-number flag value
        assertEquals(1, client.getIntegerValue(VARIANT_FLAG_NAME, 1));
    }

    @Test
    void getDoubleEvaluation() {
        MutableContext evaluationContext = new MutableContext();
        assertEquals(
                DOUBLE_FLAG_VALUE,
                prefabProvider
                        .getDoubleEvaluation(DOUBLE_FLAG_NAME, 1.1, evaluationContext)
                        .getValue());
        assertEquals(DOUBLE_FLAG_VALUE, client.getDoubleValue(DOUBLE_FLAG_NAME, 1.1));
        assertEquals(1.1, client.getDoubleValue("non-existing", 1.1));

        // non-number flag value
        assertEquals(1.1, client.getDoubleValue(VARIANT_FLAG_NAME, 1.1));
    }

    @Test
    void getBooleanEvaluationByUser() {
        MutableContext evaluationContext = new MutableContext();
        evaluationContext.add("user.key", "key1");
        evaluationContext.add("team.domain", "prefab.cloud");

        assertEquals(
                true,
                prefabProvider
                        .getBooleanEvaluation(USERS_FLAG_NAME, false, evaluationContext)
                        .getValue());
        assertEquals(true, client.getBooleanValue(USERS_FLAG_NAME, false, evaluationContext));
        evaluationContext.add("team.domain", "other.com");
        assertEquals(
                false,
                prefabProvider
                        .getBooleanEvaluation(USERS_FLAG_NAME, false, evaluationContext)
                        .getValue());
        assertEquals(false, client.getBooleanValue(USERS_FLAG_NAME, false, evaluationContext));
    }

    @SneakyThrows
    @Test
    void shouldThrowIfNotInitialized() {
        Options options = new Options()
                .setApikey("test-sdk-key")
                .setPrefabDatasource(Options.Datasources.LOCAL_ONLY)
                .setInitializationTimeoutSec(10);
        PrefabProviderConfig prefabProviderConfig =
                PrefabProviderConfig.builder().options(options).build();
        PrefabProvider tempPrefabProvider = new PrefabProvider(prefabProviderConfig);

        OpenFeatureAPI.getInstance().setProviderAndWait("tempPrefabProvider", tempPrefabProvider);

        assertThrows(GeneralError.class, () -> tempPrefabProvider.initialize(null));

        tempPrefabProvider.shutdown();
    }

    @Test
    void eventsTest() {
        prefabProvider.emitProviderReady(ProviderEventDetails.builder().build());
        prefabProvider.emitProviderError(ProviderEventDetails.builder().build());
        assertDoesNotThrow(() -> prefabProvider.emitProviderConfigurationChanged(
                ProviderEventDetails.builder().build()));
    }

    @SneakyThrows
    @Test
    void contextTransformTest() {

        MutableContext evaluationContext = new MutableContext();
        evaluationContext.add("user.key", "key1");
        evaluationContext.add("team.domain", "prefab.cloud");

        PrefabContextSet expectedContext = PrefabContextSet.from(
                PrefabContext.newBuilder("user").put("key", "key1").build(),
                PrefabContext.newBuilder("team").put("domain", "prefab.cloud").build());
        PrefabContextSetReadable transformedContext = ContextTransformer.transform(evaluationContext);

        // equals not implemented for User, using toString
        assertEquals(expectedContext.toString(), transformedContext.toString());
    }
}
