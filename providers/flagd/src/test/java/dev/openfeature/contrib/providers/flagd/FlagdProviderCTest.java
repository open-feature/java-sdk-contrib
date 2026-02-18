package dev.openfeature.contrib.providers.flagd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vmlens.api.AllInterleavings;
import com.vmlens.api.Runner;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class FlagdProviderCTest {
    private FlagdProvider provider;

    @BeforeEach
    void setup() throws Exception {
        provider = FlagdTestUtils.createInProcessProvider(Map.of(
                "flag",
                new FeatureFlag(
                        "ENABLED",
                        "a",
                        Map.of("a", "a", "b", "b", "c", "c"),
                        "{\n"
                                + "        \"if\": [\n"
                                + "          {\n"
                                + "            \"ends_with\": [\n"
                                + "              {\n"
                                + "                \"var\": \"email\"\n"
                                + "              },\n"
                                + "              \"@openfeature.dev\"\n"
                                + "            ]\n"
                                + "          },\n"
                                + "          \"b\",\n"
                                + "          \"c\"\n"
                                + "        ]\n"
                                + "      }",
                        null)));
        provider.initialize(ImmutableContext.EMPTY);
    }

    @Test
    @Disabled(
            "There is a race condition in the JsonLogic library, but it does not affect us as we don't add operators after program startup")
    void concurrentFlagEvaluationsWork() {
        var invocationContext = ImmutableContext.EMPTY;

        try (var interleavings = new AllInterleavings("Concurrent Flag evaluations")) {
            while (interleavings.hasNext()) {
                Runner.runParallel(
                        () -> assertEquals(
                                "c",
                                provider.getStringEvaluation("flag", "z", invocationContext)
                                        .getValue()),
                        () -> assertEquals(
                                "c",
                                provider.getStringEvaluation("flag", "z", invocationContext)
                                        .getValue()));
            }
        }
    }

    @Test
    void flagEvaluationsWhileSettingContextWork() {
        OpenFeatureAPI.getInstance().setProviderAndWait(provider);
        var client = OpenFeatureAPI.getInstance().getClient();

        var invocationContext = ImmutableContext.EMPTY;
        var clientContext = new ImmutableContext(Map.of("email", new Value("someone@openfeature.dev")));

        try (var interleavings = new AllInterleavings("Concurrently setting client context and evaluating a Flag")) {
            while (interleavings.hasNext()) {
                Runner.runParallel(
                        () -> assertTrue(List.of("b", "c")
                                .contains(provider.getStringEvaluation("flag", "z", invocationContext)
                                        .getValue())),
                        () -> client.setEvaluationContext(clientContext));
            }
        }
    }

    @Test
    void settingDifferentContextsWorks() {
        OpenFeatureAPI.getInstance().setProviderAndWait(provider);
        var client = OpenFeatureAPI.getInstance().getClient();

        var invocationContext = ImmutableContext.EMPTY;
        var clientContext = new ImmutableContext(Map.of("email", new Value("someone@openfeature.dev")));
        var apiContext = new ImmutableContext(Map.of("email", new Value("someone.else@test.com")));

        try (var interleavings = new AllInterleavings("Concurrently setting client and api context")) {
            while (interleavings.hasNext()) {
                Runner.runParallel(
                        () -> client.setEvaluationContext(clientContext),
                        () -> OpenFeatureAPI.getInstance().setEvaluationContext(apiContext),
                        () -> assertTrue(List.of("b", "c")
                                .contains(provider.getStringEvaluation("flag", "z", invocationContext)
                                        .getValue())));
            }
        }
    }
}
