package dev.openfeature.contrib.providers.gofeatureflag.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Value;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EvaluationContextUtilTest {

    @Test
    void testIsAnonymousUser_WhenContextIsNull_ShouldReturnTrue() {
        assertTrue(EvaluationContextUtil.isAnonymousUser(null), "Expected true when context is null");
    }

    @Test
    void testIsAnonymousUser_WhenAnonymousFieldIsTrue_ShouldReturnTrue() {
        MutableContext ctx = new MutableContext();
        ctx.add("anonymous", true);
        assertTrue(EvaluationContextUtil.isAnonymousUser(ctx), "Expected true when anonymous field is true");
    }

    @Test
    void testIsAnonymousUser_WhenAnonymousFieldIsFalse_ShouldReturnFalse() {
        MutableContext ctx = new MutableContext();
        ctx.add("anonymous", false);
        assertFalse(EvaluationContextUtil.isAnonymousUser(ctx), "Expected false when anonymous field is false");
    }

    @Test
    void testIsAnonymousUser_WhenAnonymousFieldIsMissing_ShouldReturnFalse() {
        MutableContext ctx = new MutableContext();
        assertFalse(EvaluationContextUtil.isAnonymousUser(ctx), "Expected false when anonymous field is missing");
    }

    @DisplayName("any non-boolean value under the attribute should not make the user anonymous")
    @ParameterizedTest(name = "anonymous = {0}")
    @MethodSource("nonBooleanValues")
    void nonBooleanValuesShouldNotMakeTheUserAnonymous(String label, Value value) {
        val ctx = new ImmutableContext(Map.of("anonymous", value));
        assertFalse(
                EvaluationContextUtil.isAnonymousUser(ctx),
                "a " + label + " under the attribute was read as an assertion of anonymity");
    }

    private static Stream<Arguments> nonBooleanValues() {
        return Stream.of(
                Arguments.of("string \"true\"", new Value("true")),
                Arguments.of("string", new Value("yes")),
                Arguments.of("number", new Value(1)),
                Arguments.of("zero", new Value(0)),
                Arguments.of("list", new Value(List.of(new Value(true)))),
                Arguments.of("structure", new Value(new MutableStructure().add("anonymous", true))),
                Arguments.of("null value", new Value()));
    }
}
