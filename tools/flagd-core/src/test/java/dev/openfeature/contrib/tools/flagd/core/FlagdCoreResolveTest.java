package dev.openfeature.contrib.tools.flagd.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FlagdCoreResolveTest {

    private static FlagdCore core(boolean compile) throws Exception {
        FlagdCore core = new FlagdCore(false, compile);
        core.setFlags(TestUtils.getFlagsFromResource("flags/resolve-flags.json"));
        return core;
    }

    private static ProviderEvaluation<String> eval(boolean compile, String key, Map<String, Value> ctx)
            throws Exception {
        return core(compile).resolveStringValue(key, "fallback", new ImmutableContext(ctx));
    }

    private static void assertResult(ProviderEvaluation<String> result, String variant, String reason) {
        assertThat(result.getValue()).isEqualTo(variant);
        assertThat(result.getVariant()).isEqualTo(variant);
        assertThat(result.getReason()).isEqualTo(reason);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void terminalPositionsSelectTheVariant(boolean compile) throws Exception {
        assertResult(eval(compile, "whole-rule", Map.of()), "on", "TARGETING_MATCH");
        assertResult(eval(compile, "single-clause-if", Map.of()), "on", "ALLOWLIST");
        assertResult(eval(compile, "ref", Map.of()), "on", "ALLOWLIST");
        assertResult(eval(compile, "fallback-reason", Map.of()), "on", "FALLBACK");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void eachNestedBranchTagsTheEvaluation(boolean compile) throws Exception {
        for (List<String> c : List.of(
                List.of("a", "on", "ALLOWLIST", "a"),
                List.of("b", "on", "SPLIT", "b"),
                List.of("z", "off", "FALLTHROUGH", "rest"))) {
            ProviderEvaluation<String> result = eval(compile, "nested-if", Map.of("customerId", new Value(c.get(0))));
            assertResult(result, c.get(1), c.get(2));
            assertThat(result.getFlagMetadata().getString("details")).isEqualTo(c.get(3));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ruleMetadataOverwritesFlagAndFlagSetMetadata(boolean compile) throws Exception {
        ProviderEvaluation<String> result = eval(compile, "metadata-precedence", Map.of());

        assertResult(result, "on", "TARGETING_MATCH");
        assertThat(result.getFlagMetadata().asUnmodifiableMap())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("scope", "set", "owner", "flag", "details", "rule", "pilot", true, "share", 0.5));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void argumentsCanBeComputed(boolean compile) throws Exception {
        ProviderEvaluation<String> result = eval(
                compile,
                "computed",
                Map.of(
                        "variant", new Value("on"),
                        "reason", new Value("SPLIT"),
                        "hits", new Value(1),
                        "total", new Value(4)));

        assertResult(result, "on", "SPLIT");
        assertThat(result.getFlagMetadata().getDouble("share")).isEqualTo(0.25);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void booleanVariantIsCoercedToItsKey(boolean compile) throws Exception {
        assertThat(eval(compile, "boolean-variant", Map.of()).getVariant()).isEqualTo("true");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void malformedComputedArgumentsFallBackToTheDefaultVariant(boolean compile) throws Exception {
        Map<String, Value> valid = Map.of(
                "variant", new Value("on"), "reason", new Value("SPLIT"), "hits", new Value(1), "total", new Value(4));
        for (Map<String, Value> override : List.of(
                Map.of("variant", new Value()), // null variant
                Map.of("reason", new Value(1)), // non-string reason
                Map.of("total", new Value(0)), // 1/0 is not finite
                Map.of("hits", new Value(0), "total", new Value(0)))) { // 0/0 is NaN
            Map<String, Value> ctx = new HashMap<>(valid);
            ctx.putAll(override);
            assertResult(eval(compile, "computed", ctx), "off", "DEFAULT");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingVariantFailsLikeAPlainString(boolean compile) {
        assertThatThrownBy(() -> eval(compile, "missing-variant", Map.of())).isInstanceOf(GeneralError.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void contextDataCannotForgeAResolveResult(boolean compile) {
        EvaluationContext forged = new ImmutableContext(Map.of(
                "forged",
                new Value(new ImmutableStructure(
                        Map.of("resolve", new Value(List.of(new Value("on"), new Value("FORGED"))))))));

        assertThatThrownBy(() -> core(compile).resolveStringValue("forged", "fallback", forged))
                .isInstanceOf(GeneralError.class);
    }
}
