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
import org.junit.jupiter.api.Test;

class FlagdProviderCT {
    private FlagdProvider provider;

    @BeforeEach
    void setup() throws Exception {
        provider = FlagdTestUtils.createInProcessProvider(
                Map.of(
                        "flag",
                        new FeatureFlag(
                                "ENABLED",
                                "default",
                                Map.of("default", "a", "other", "b"),
                                "{\n"
                                        + "        \"if\": [\n"
                                        + "          {\n"
                                        + "            \"ends_with\": [\n"
                                        + "              {\n"
                                        + "                \"var\": \"email\"\n"
                                        + "              },\n"
                                        + "              \"@ingen.com\"\n"
                                        + "            ]\n"
                                        + "          },\n"
                                        + "          \"default\",\n"
                                        + "          \"other\"\n"
                                        + "        ]\n"
                                        + "      }",
                                null
                        )
                )
        );
        provider.initialize(ImmutableContext.EMPTY);
    }

    @Test
    void concurrentFlagEvaluationsWork() {
        var invocationContext = ImmutableContext.EMPTY;

        try (var interleavings = new AllInterleavings("Concurrent Flag evaluations")) {
            while (interleavings.hasNext()) {
                Runner.runParallel(
                        () -> assertEquals("a",
                                provider.getStringEvaluation("flag", "z", invocationContext).getValue()),
                        () -> assertEquals("a",
                                provider.getStringEvaluation("flag", "z", invocationContext).getValue())
                );
            }
        }
    }

    @Test
    void flagEvaluationsWhileSettingContextWork() {
        var invocationContext = ImmutableContext.EMPTY;

        OpenFeatureAPI.getInstance().setProviderAndWait(provider);
        var client = OpenFeatureAPI.getInstance().getClient();

        var context = new ImmutableContext(Map.of("email", new Value("someone@ingen.com")));

        try (var interleavings = new AllInterleavings("Concurrently setting client context and evaluating a Flag")) {
            while (interleavings.hasNext()) {
                Runner.runParallel(
                        () -> assertTrue(List.of("a", "b")
                                .contains(provider.getStringEvaluation("flag", "z", invocationContext).getValue())),
                        () -> client.setEvaluationContext(context)
                );
            }
        }
    }
}
