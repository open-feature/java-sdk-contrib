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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FlagdCoreResolveTest {

    // valid arguments for the "computed" flag
    private static final Map<String, Value> COMPUTED_CTX = Map.of(
            "variant", new Value("on"),
            "reason", new Value("SPLIT"),
            "key", new Value("share"),
            "hits", new Value(1),
            "total", new Value(4));

    private static FlagdCore core(boolean compile) throws Exception {
        FlagdCore core = new FlagdCore(true, compile);
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
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reasonIsFreeForm(boolean compile) throws Exception {
        assertResult(eval(compile, "fallback-reason", Map.of()), "on", "FALLBACK");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void literalRuleMetadataCannotOverrideReservedKeys(boolean compile) throws Exception {
        String flags = TestUtils.getFlagsFromResource("flags/resolve-reserved-literal-flags.json");
        assertThatThrownBy(() -> new FlagdCore(true, compile).setFlags(flags))
                .isInstanceOf(IllegalArgumentException.class);

        // loaded anyway, the runtime check still skips the reserved keys
        FlagdCore core = new FlagdCore(false, compile);
        core.setFlags(flags);
        ProviderEvaluation<String> result =
                core.resolveStringValue("reserved-literal", "fallback", new ImmutableContext());

        assertResult(result, "on", "TARGETING_MATCH");
        assertThat(result.getFlagMetadata().getString("flagSetId")).isEqualTo("set-id");
        assertThat(result.getFlagMetadata().getString("version")).isEqualTo("set-version");
        assertThat(result.getFlagMetadata().getString("details")).isEqualTo("rule");
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
    void eachFractionalBucketTagsTheEvaluation(boolean compile) throws Exception {
        for (boolean treatment : new boolean[] {true, false}) {
            ProviderEvaluation<String> result =
                    eval(compile, "fractional-buckets", Map.of("treatment", new Value(treatment)));
            assertResult(result, treatment ? "on" : "off", "SPLIT");
            assertThat(result.getFlagMetadata().getString("details")).isEqualTo(treatment ? "treatment" : "holdback");
        }
    }

    @ParameterizedTest
    @CsvSource({
        "false,fractional-shorthand",
        "false,fractional-shorthand-single",
        "true,fractional-shorthand",
        "true,fractional-shorthand-single"
    })
    void shorthandFractionalBucketTagsTheEvaluation(boolean compile, String flag) throws Exception {
        ProviderEvaluation<String> result = eval(compile, flag, Map.of("targetingKey", new Value("user")));

        assertResult(result, "on", "SPLIT");
        assertThat(result.getFlagMetadata().getString("details")).isEqualTo("only");
    }

    @ParameterizedTest
    @CsvSource({
        "false,computed-metadata-list",
        "false,computed-metadata-pair",
        "true,computed-metadata-list",
        "true,computed-metadata-pair"
    })
    void wholeMetadataListAndPairCanBeComputed(boolean compile, String flag) throws Exception {
        for (boolean pick : new boolean[] {true, false}) {
            ProviderEvaluation<String> result = eval(compile, flag, Map.of("pick", new Value(pick)));
            assertResult(result, "on", "TARGETING_MATCH");
            assertThat(result.getFlagMetadata().getString("details")).isEqualTo(pick ? "a" : "b");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ruleMetadataOverwritesFlagAndFlagSetMetadata(boolean compile) throws Exception {
        ProviderEvaluation<String> result = eval(compile, "metadata-precedence", Map.of());

        assertResult(result, "on", "TARGETING_MATCH");
        assertThat(result.getFlagMetadata().asUnmodifiableMap())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "scope", "set",
                        "owner", "flag",
                        "flagSetId", "set-id",
                        "version", "set-version",
                        "details", "rule",
                        "pilot", true,
                        "share", 0.5,
                        "count", 3));
    }

    @ParameterizedTest
    @CsvSource({"false,flagSetId", "false,version", "true,flagSetId", "true,version"})
    void computedRuleMetadataCannotOverrideReservedKeys(boolean compile, String computedKey) throws Exception {
        ProviderEvaluation<String> result = eval(compile, "reserved-keys", Map.of("key", new Value(computedKey)));

        assertResult(result, "on", "TARGETING_MATCH");
        assertThat(result.getFlagMetadata().getString("flagSetId")).isEqualTo("set-id");
        assertThat(result.getFlagMetadata().getString("version")).isEqualTo("set-version");
        assertThat(result.getFlagMetadata().getString("details")).isEqualTo("rule");
    }

    @ParameterizedTest
    @CsvSource({"false,ERROR", "false,DISABLED", "false,DEFAULT", "true,ERROR", "true,DISABLED", "true,DEFAULT"})
    void computedReservedReasonIsIgnored(boolean compile, String reason) throws Exception {
        Map<String, Value> ctx = new HashMap<>(COMPUTED_CTX);
        ctx.put("reason", new Value(reason));
        ProviderEvaluation<String> result = eval(compile, "computed", ctx);

        assertResult(result, "on", "TARGETING_MATCH");
        assertThat(result.getFlagMetadata().getDouble("share")).isEqualTo(0.25);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void argumentsCanBeComputed(boolean compile) throws Exception {
        ProviderEvaluation<String> result = eval(compile, "computed", COMPUTED_CTX);

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
        for (Map<String, Value> override : List.of(
                Map.of("variant", new Value()), // null variant
                Map.of("reason", new Value(1)), // non-string reason
                Map.of("key", new Value(1)), // non-string metadata key
                Map.of("total", new Value(0)), // 1/0 is not finite
                Map.of("hits", new Value(0), "total", new Value(0)))) { // 0/0 is NaN
            Map<String, Value> ctx = new HashMap<>(COMPUTED_CTX);
            ctx.putAll(override);
            assertResult(eval(compile, "computed", ctx), "off", "DEFAULT");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void arrayValuedSoleArgumentIsNotSpreadIntoArguments(boolean compile) throws Exception {
        Map<String, Value> ctx = Map.of("candidate", new Value(List.of(new Value("on"), new Value("CUSTOM"))));

        assertResult(eval(compile, "sole-computed-argument", ctx), "off", "DEFAULT");
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
