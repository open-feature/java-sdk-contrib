package dev.openfeature.contrib.providers.optimizely;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.optimizely.ab.Optimizely;
import com.optimizely.ab.OptimizelyUserContext;
import com.optimizely.ab.bucketing.UserProfileService;
import com.optimizely.ab.config.ProjectConfigManager;
import com.optimizely.ab.error.ErrorHandler;
import com.optimizely.ab.event.EventProcessor;
import com.optimizely.ab.odp.ODPManager;
import com.optimizely.ab.optimizelydecision.OptimizelyDecision;
import com.optimizely.ab.optimizelyjson.OptimizelyJSON;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class OptimizelyProviderTest {

    @Test
    public void test_constructor_initializes_provider_with_valid_config() {
        OptimizelyProviderConfig config = OptimizelyProviderConfig.builder()
            .projectConfigManager(mock(ProjectConfigManager.class))
            .eventProcessor(mock(EventProcessor.class))
            .datafile("test-datafile")
            .build();

        OptimizelyProvider provider = new OptimizelyProvider(config);

        assertThat(provider).isNotNull();
        assertEquals("Optimizely", provider.getMetadata().getName());
    }

    @Test
    public void test_initialize_handles_null_configuration_parameters() {
        OptimizelyProviderConfig config = OptimizelyProviderConfig.builder()
            .projectConfigManager(null)
            .eventProcessor(null)
            .datafile(null)
            .build();

        OptimizelyProvider provider = new OptimizelyProvider(config);
        EvaluationContext evaluationContext = mock(EvaluationContext.class);

        assertDoesNotThrow(() -> {
            provider.initialize(evaluationContext);
        });
    }

    @Test
    public void test_initialize_builds_optimizely_and_context_transformer() throws Exception {
        OptimizelyProviderConfig config = mock(OptimizelyProviderConfig.class);
        when(config.getProjectConfigManager()).thenReturn(mock(ProjectConfigManager.class));
        OptimizelyProvider provider = new OptimizelyProvider(config);
        provider.initialize(mock(EvaluationContext.class));
    }

    @SneakyThrows
    @Test
    public void test_get_string_evaluation_returns_correct_value() {
        OptimizelyProviderConfig config = mock(OptimizelyProviderConfig.class);
        ContextTransformer contextTransformer = mock(ContextTransformer.class);
        OptimizelyUserContext userContext = mock(OptimizelyUserContext.class);
        OptimizelyDecision decision = mock(OptimizelyDecision.class);

        when(contextTransformer.transform(any(EvaluationContext.class))).thenReturn(userContext);
        when(userContext.decide(anyString())).thenReturn(decision);
        when(decision.getVariationKey()).thenReturn("variationKey");
        when(decision.getEnabled()).thenReturn(true);

        OptimizelyProvider provider = new OptimizelyProvider(config);
        provider.initialize(new MutableContext());

        EvaluationContext ctx = new MutableContext("targetingKey");
        ProviderEvaluation<String> result = provider.getStringEvaluation("featureKey", "defaultValue", ctx);

        assertEquals("variationKey", result.getValue());

        when(decision.getEnabled()).thenReturn(false);
        result = provider.getStringEvaluation("featureKey", "defaultValue", ctx);

        assertEquals("defaultValue", result.getValue());
    }

    @SneakyThrows
    @Test
    public void test_get_object_evaluation_returns_transformed_variables() {
        OptimizelyProviderConfig config = mock(OptimizelyProviderConfig.class);
        ContextTransformer contextTransformer = mock(ContextTransformer.class);
        OptimizelyUserContext userContext = mock(OptimizelyUserContext.class);
        OptimizelyDecision decision = mock(OptimizelyDecision.class);
        OptimizelyJSON optimizelyJSON = mock(OptimizelyJSON.class);

        when(contextTransformer.transform(any(EvaluationContext.class))).thenReturn(userContext);
        when(userContext.decide(anyString())).thenReturn(decision);
        when(decision.getEnabled()).thenReturn(true);
        when(decision.getVariables()).thenReturn(optimizelyJSON);
        when(optimizelyJSON.toMap()).thenReturn(Map.of("key", "value"));

        OptimizelyProvider provider = new OptimizelyProvider(config);
        provider.initialize(mock(EvaluationContext.class));

        EvaluationContext ctx = new MutableContext("targetingKey");
        ProviderEvaluation<Value> result = provider.getObjectEvaluation("featureKey", new Value(), ctx);

        assertNotNull(result.getValue());
        assertNotNull(result.getValue().asStructure().getValue("variables"));
    }

    @Test
    public void test_get_boolean_evaluation_handles_null_variation_key() {
        OptimizelyProviderConfig config = mock(OptimizelyProviderConfig.class);
        ContextTransformer contextTransformerMock = mock(ContextTransformer.class);
        OptimizelyUserContext userContextMock = mock(OptimizelyUserContext.class);
        OptimizelyDecision decisionMock = mock(OptimizelyDecision.class);

        when(contextTransformerMock.transform(any())).thenReturn(userContextMock);
        when(userContextMock.decide(anyString())).thenReturn(decisionMock);
        when(decisionMock.getVariationKey()).thenReturn(null);
        when(decisionMock.getReasons()).thenReturn(List.of("reason1", "reason2"));
        when(decisionMock.getEnabled()).thenReturn(false);

        OptimizelyProvider provider = new OptimizelyProvider(config);

        ProviderEvaluation<Boolean> evaluation = provider.getBooleanEvaluation("key", false, mock(EvaluationContext.class));

        assertFalse(evaluation.getValue());
        assertEquals("reason1, reason2", evaluation.getReason());
    }
}
