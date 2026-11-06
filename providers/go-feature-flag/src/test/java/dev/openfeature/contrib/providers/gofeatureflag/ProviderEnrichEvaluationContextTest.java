package dev.openfeature.contrib.providers.gofeatureflag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.sdk.OpenFeatureAPI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Provider evaluation context enrichment")
class ProviderEnrichEvaluationContextTest extends AbstractGoFeatureFlagProviderTest {
    @DisplayName("Should add to the context the exporter metadata to the evaluation context")
    @SneakyThrows
    @Test
    void shouldAddToTheContextTheExporterMetadataToTheEvaluationContext() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .exporterMetadata(Map.of(
                        "test-string", "testing-provider", "test-int", 1, "test-double", 3.14, "test-boolean", true))
                .evaluationType(EvaluationType.REMOTE)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        client.getBooleanDetails("bool_flag", false, TestUtils.defaultEvaluationContext);
        val got = Const.DESERIALIZE_OBJECT_MAPPER.readValue(goffAPIMock.getLastRequestBody(), HashMap.class);

        val context = new HashMap<String, Object>();
        context.put("targetingKey", "d45e303a-38c2-11ed-a261-0242ac120002");
        context.put("rate", 3.14);
        context.put("company_info", Map.of("size", 120, "name", "my_company"));
        context.put("anonymous", false);
        context.put("email", "john.doe@gofeatureflag.org");
        context.put("lastname", "doe");
        context.put("firstname", "john");
        context.put("age", 30);
        context.put(
                "gofeatureflag",
                Map.of(
                        "exporterMetadata",
                        Map.of(
                                "test-double",
                                3.14,
                                "test-int",
                                1,
                                "test-boolean",
                                true,
                                "test-string",
                                "testing-provider",
                                "provider",
                                "java",
                                "openfeature",
                                true)));
        context.put("professional", true);
        context.put("labels", List.of("pro", "beta"));

        Map<String, Object> want = new HashMap<>();
        want.put("context", context);
        assertEquals(want, got);
    }

    @DisplayName("Should add the reserved exporter metadata even if the user configured none")
    @SneakyThrows
    @Test
    void shouldAddTheReservedExporterMetadataEvenIfTheUserConfiguredNone() {
        GoFeatureFlagProvider provider = new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint(baseUrl.toString())
                .evaluationType(EvaluationType.REMOTE)
                .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(testName, provider);
        val client = OpenFeatureAPI.getInstance().getClient(testName);
        client.getBooleanDetails("bool_flag", false, TestUtils.defaultEvaluationContext);
        val got = Const.DESERIALIZE_OBJECT_MAPPER.readValue(goffAPIMock.getLastRequestBody(), HashMap.class);

        val context = new HashMap<String, Object>();
        context.put("targetingKey", "d45e303a-38c2-11ed-a261-0242ac120002");
        context.put("rate", 3.14);
        context.put("company_info", Map.of("size", 120, "name", "my_company"));
        context.put("anonymous", false);
        context.put("email", "john.doe@gofeatureflag.org");
        context.put("lastname", "doe");
        context.put("firstname", "john");
        context.put("age", 30);
        context.put("professional", true);
        context.put("labels", List.of("pro", "beta"));
        context.put("gofeatureflag", Map.of("exporterMetadata", Map.of("provider", "java", "openfeature", true)));

        Map<String, Object> want = new HashMap<>();
        want.put("context", context);
        assertEquals(want, got, "a provider with no configured metadata is unattributable to an SDK");
    }
}
