package dev.openfeature.contrib.providers.optimizely;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.optimizely.ab.config.ProjectConfigManager;
import com.optimizely.ab.event.EventProcessor;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import java.io.File;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OptimizelyProviderTest {

    private static OptimizelyProvider provider;

    @SneakyThrows
    @BeforeAll
    static void setUp() {
        File dataFile = new File(
            OptimizelyProviderTest.class.getClassLoader().getResource("data.json").getFile());
        String dataFileContent = new String(java.nio.file.Files.readAllBytes(dataFile.toPath()));

        OptimizelyProviderConfig config = OptimizelyProviderConfig.builder()
            .eventProcessor(mock(EventProcessor.class))
            .datafile(dataFileContent)
            .build();

        provider = new OptimizelyProvider(config);
        provider.initialize(new MutableContext("test-targeting-key"));
    }

    @Test
    public void test_constructor_initializes_provider_with_valid_config() {
        OptimizelyProviderConfig config = OptimizelyProviderConfig.builder()
            .projectConfigManager(mock(ProjectConfigManager.class))
            .eventProcessor(mock(EventProcessor.class))
            .datafile("test-datafile")
            .build();

        OptimizelyProvider localProvider = new OptimizelyProvider(config);

        assertThat(localProvider).isNotNull();
        assertEquals("Optimizely", localProvider.getMetadata().getName());
    }

    @Test
    public void test_initialize_handles_null_configuration_parameters() {
        OptimizelyProviderConfig config = OptimizelyProviderConfig.builder()
            .projectConfigManager(null)
            .eventProcessor(null)
            .datafile(null)
            .build();

        OptimizelyProvider localProvider = new OptimizelyProvider(config);
        EvaluationContext evaluationContext = mock(EvaluationContext.class);

        assertDoesNotThrow(() -> {
            localProvider.initialize(evaluationContext);
        });
    }

    @SneakyThrows
    @Test
    public void test_get_object_evaluation_returns_transformed_variables() {
        EvaluationContext ctx = new MutableContext("targetingKey");
        ProviderEvaluation<Value> result = provider.getObjectEvaluation("string-feature", new Value(), ctx);

        assertNotNull(result.getValue());
        assertEquals("string_feature_variation", result.getVariant());
        assertEquals("str1", result.getValue().asStructure().getValue("string_variable_1").asString());
    }

    @Test
    public void test_get_boolean_evaluation_handles_null_variation_key() {
        EvaluationContext ctx = new MutableContext("targetingKey");
        ProviderEvaluation<Boolean> evaluation = provider.getBooleanEvaluation("string-feature", false, ctx);

        assertTrue(evaluation.getValue());
    }
}
