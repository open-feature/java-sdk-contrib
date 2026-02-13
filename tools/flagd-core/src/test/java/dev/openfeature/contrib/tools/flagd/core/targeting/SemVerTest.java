package dev.openfeature.contrib.tools.flagd.core.targeting;

import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SemVerTest {

    static Stream<Arguments> validInputs() {
        return Stream.of(
                Arguments.of(Arrays.asList("v1.2.3", "=", "1.2.3")),
                Arguments.of(Arrays.asList("v1.2.3", "!=", "1.2.4")),
                Arguments.of(Arrays.asList("v1.2.3", "<", "1.2.4")),
                Arguments.of(Arrays.asList("v1.2.3", "<=", "1.2.3")),
                Arguments.of(Arrays.asList("v1.2.3", ">=", "v1.2.3")),
                Arguments.of(Arrays.asList("v1.2.3", "^", "v1.0.0")),
                Arguments.of(Arrays.asList("v5.0.3", "~", "v5.0.8")),
                Arguments.of(Arrays.asList("v5.0.3", "~", "v5.0.8")));
    }

    @ParameterizedTest
    @MethodSource("validInputs")
    void testValidCases(List<String> args) throws JsonLogicEvaluationException {
        // given
        final SemVer semVer = new SemVer();

        // when
        Object result = semVer.evaluate(args, new Object(), "jsonPath");

        // then
        if (!(result instanceof Boolean)) {
            fail("Result is not of type Boolean");
        }

        assertTrue((Boolean) result);
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of(Arrays.asList("1.2.3", "=", 1.2)),
                Arguments.of(Arrays.asList("1.2", "=", "1.2.3")),
                Arguments.of(Arrays.asList("1.2.3", "*", "1.2.3")),
                Arguments.of(Arrays.asList("1.2.3", "=", "1.2")));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void testInvalidCases(List args) throws JsonLogicEvaluationException {
        // given
        final SemVer semVer = new SemVer();

        // then
        assertNull(semVer.evaluate(args, new Object(), "jsonPath"));
    }
}
