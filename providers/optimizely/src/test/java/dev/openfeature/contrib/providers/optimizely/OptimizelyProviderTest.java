package dev.openfeature.contrib.providers.optimizely;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.optimizely.ab.config.ProjectConfigManager;
import com.optimizely.ab.event.EventProcessor;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.TargetingKeyMissingError;
import java.io.File;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OptimizelyProviderTest {

    private static OptimizelyProvider provider;

    @SneakyThrows
    @BeforeAll
    static void setUp() {
        File dataFile = new File(OptimizelyProviderTest.class
                .getClassLoader()
                .getResource("data.json")
                .getFile());
        String dataFileContent = new String(java.nio.file.Files.readAllBytes(dataFile.toPath()));

        OptimizelyProviderConfig config = OptimizelyProviderConfig.builder()
                .eventProcessor(mock(EventProcessor.class))
                .datafile(dataFileContent)
                .build();

        provider = new OptimizelyProvider(config);
        provider.initialize(new MutableContext("test-targeting-key"));
    }

    @Test
    public void testConstructorInitializesProviderWithValidConfig() {
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
    public void testInitializeHandlesNullConfigurationParameters() {
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
    public void testGetObjectEvaluation() {
        EvaluationContext ctx = new MutableContext("targetingKey");
        ProviderEvaluation<Value> evaluation = provider.getObjectEvaluation("json_feature_flag", new Value(), ctx);

        assertEquals(
                "{key1=value1, key2=2.0}",
                evaluation.getValue().asStructure().asObjectMap().toString());

        MutableContext contextWithVarKey = new MutableContext("targetingKey");
        contextWithVarKey.add("variableKey", "var_json");

        evaluation = provider.getObjectEvaluation("json_feature_flag", new Value(), contextWithVarKey);

        assertEquals(
                "{key1=value1a, key2=3.0}",
                evaluation.getValue().asStructure().asObjectMap().toString());
        assertEquals("var_json", evaluation.getVariant());
        assertEquals(Reason.TARGETING_MATCH.name(), evaluation.getReason());

        MutableContext contextWithNonExistingVarKey = new MutableContext("targetingKey");
        contextWithNonExistingVarKey.add("variableKey", "non-existing-var");

        evaluation = provider.getObjectEvaluation("json_feature_flag", new Value(), contextWithNonExistingVarKey);

        assertTrue(evaluation.getValue().isNull());
        assertEquals(null, evaluation.getVariant());
        assertEquals(Reason.DEFAULT.name(), evaluation.getReason());

        EvaluationContext emptyEvaluationContext = new MutableContext();
        assertThrows(TargetingKeyMissingError.class, () -> {
            provider.getObjectEvaluation("string-feature", new Value(), emptyEvaluationContext);
        });

        evaluation = provider.getObjectEvaluation("non-existing-feature", new Value(), ctx);
        assertTrue(evaluation.getValue().isNull());
        assertEquals(null, evaluation.getVariant());
        assertEquals(Reason.DEFAULT.name(), evaluation.getReason());
    }

    @Test
    public void testGetBooleanEvaluation() {
        EvaluationContext ctx = new MutableContext("targetingKey");
        ProviderEvaluation<Boolean> evaluation = provider.getBooleanEvaluation("boolean_feature_flag", false, ctx);

        assertTrue(evaluation.getValue());
        assertEquals("value", evaluation.getVariant());
        assertEquals(Reason.TARGETING_MATCH.name(), evaluation.getReason());

        MutableContext contextWithVarKey = new MutableContext("targetingKey");
        contextWithVarKey.add("variableKey", "var_bool");

        evaluation = provider.getBooleanEvaluation("boolean_feature_flag", false, contextWithVarKey);

        assertTrue(evaluation.getValue());
        assertEquals("var_bool", evaluation.getVariant());
        assertEquals(Reason.TARGETING_MATCH.name(), evaluation.getReason());

        MutableContext contextWithNonExistingVarKey = new MutableContext("targetingKey");
        contextWithNonExistingVarKey.add("variableKey", "non-existing-var");

        evaluation = provider.getBooleanEvaluation("boolean_feature_flag", false, contextWithNonExistingVarKey);

        assertFalse(evaluation.getValue());
        assertEquals(null, evaluation.getVariant());
        assertEquals(Reason.DEFAULT.name(), evaluation.getReason());

        EvaluationContext emptyEvaluationContext = new MutableContext();
        assertThrows(TargetingKeyMissingError.class, () -> {
            provider.getBooleanEvaluation("string-feature", false, emptyEvaluationContext);
        });

        evaluation = provider.getBooleanEvaluation("non-existing-feature", false, ctx);
        assertFalse(evaluation.getValue());
        assertEquals(null, evaluation.getVariant());
        assertEquals(Reason.DEFAULT.name(), evaluation.getReason());
    }

    @Test
    public void testGetStringEvaluation() {
        EvaluationContext ctx = new MutableContext("targetingKey");
        ProviderEvaluation<String> evaluation = provider.getStringEvaluation("string_feature_flag", "", ctx);

        assertEquals("str1", evaluation.getValue());

        MutableContext contextWithVarKey = new MutableContext("targetingKey");
        contextWithVarKey.add("variableKey", "var_str");

        evaluation = provider.getStringEvaluation("string_feature_flag", "", contextWithVarKey);

        assertEquals("str2", evaluation.getValue());
        assertEquals("var_str", evaluation.getVariant());
        assertEquals(Reason.TARGETING_MATCH.name(), evaluation.getReason());

        MutableContext contextWithNonExistingVarKey = new MutableContext("targetingKey");
        contextWithNonExistingVarKey.add("variableKey", "non-existing-var");

        evaluation = provider.getStringEvaluation("string_feature_flag", "", contextWithNonExistingVarKey);

        assertEquals("", evaluation.getValue());
        assertEquals(null, evaluation.getVariant());
        assertEquals(Reason.DEFAULT.name(), evaluation.getReason());

        EvaluationContext emptyEvaluationContext = new MutableContext();
        assertThrows(TargetingKeyMissingError.class, () -> {
            provider.getStringEvaluation("string-feature", "", emptyEvaluationContext);
        });

        evaluation = provider.getStringEvaluation("non-existing-feature", "", ctx);
        assertEquals("", evaluation.getValue());
        assertEquals(null, evaluation.getVariant());
        assertEquals(Reason.DEFAULT.name(), evaluation.getReason());
    }

    @Test
    public void testGetIntegerEvaluation() {
        EvaluationContext ctx = new MutableContext("targetingKey");
        ProviderEvaluation<Integer> evaluation = provider.getIntegerEvaluation("int_feature_flag", 0, ctx);

        assertEquals(1, evaluation.getValue());

        MutableContext contextWithVarKey = new MutableContext("targetingKey");
        contextWithVarKey.add("variableKey", "var_int");

        evaluation = provider.getIntegerEvaluation("int_feature_flag", 0, contextWithVarKey);

        assertEquals(2, evaluation.getValue());
        assertEquals("var_int", evaluation.getVariant());
        assertEquals(Reason.TARGETING_MATCH.name(), evaluation.getReason());

        MutableContext contextWithNonExistingVarKey = new MutableContext("targetingKey");
        contextWithNonExistingVarKey.add("variableKey", "non-existing-var");

        evaluation = provider.getIntegerEvaluation("int_feature_flag", 0, contextWithNonExistingVarKey);

        assertEquals(0, evaluation.getValue());
        assertEquals(null, evaluation.getVariant());
        assertEquals(Reason.DEFAULT.name(), evaluation.getReason());

        EvaluationContext emptyEvaluationContext = new MutableContext();
        assertThrows(TargetingKeyMissingError.class, () -> {
            provider.getIntegerEvaluation("string-feature", 0, emptyEvaluationContext);
        });

        evaluation = provider.getIntegerEvaluation("non-existing-feature", 0, ctx);
        assertEquals(0, evaluation.getValue());
        assertEquals(null, evaluation.getVariant());
        assertEquals(Reason.DEFAULT.name(), evaluation.getReason());
    }

    @Test
    public void testGetDoubleEvaluation() {
        EvaluationContext ctx = new MutableContext("targetingKey");
        ProviderEvaluation<Double> evaluation = provider.getDoubleEvaluation("double_feature_flag", 0.0, ctx);

        assertEquals(1.5, evaluation.getValue());

        MutableContext contextWithVarKey = new MutableContext("targetingKey");
        contextWithVarKey.add("variableKey", "var_double");

        evaluation = provider.getDoubleEvaluation("double_feature_flag", 0.0, contextWithVarKey);

        assertEquals(2.5, evaluation.getValue());
        assertEquals("var_double", evaluation.getVariant());
        assertEquals(Reason.TARGETING_MATCH.name(), evaluation.getReason());

        MutableContext contextWithNonExistingVarKey = new MutableContext("targetingKey");
        contextWithNonExistingVarKey.add("variableKey", "non-existing-var");

        evaluation = provider.getDoubleEvaluation("double_feature_flag", 0.0, contextWithNonExistingVarKey);

        assertEquals(0.0, evaluation.getValue());
        assertEquals(null, evaluation.getVariant());
        assertEquals(Reason.DEFAULT.name(), evaluation.getReason());

        EvaluationContext emptyEvaluationContext = new MutableContext();
        assertThrows(TargetingKeyMissingError.class, () -> {
            provider.getDoubleEvaluation("string-feature", 0.0, emptyEvaluationContext);
        });

        evaluation = provider.getDoubleEvaluation("non-existing-feature", 0.0, ctx);
        assertEquals(0.0, evaluation.getValue());
        assertEquals(null, evaluation.getVariant());
        assertEquals(Reason.DEFAULT.name(), evaluation.getReason());
    }

    @SneakyThrows
    @AfterAll
    static void tearDown() {
        if (provider != null) {
            provider.shutdown();
        }
    }
}
