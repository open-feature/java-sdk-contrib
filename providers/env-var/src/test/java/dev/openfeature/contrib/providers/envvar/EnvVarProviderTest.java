package dev.openfeature.contrib.providers.envvar;

import dev.openfeature.sdk.*;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.ParseError;
import dev.openfeature.sdk.exceptions.ValueNotConvertableError;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvVarProviderTest {

    @Test
    void shouldThrowOnGetObjectEvaluation() {
        assertThrows(
                ValueNotConvertableError.class,
                () -> new EnvVarProvider().getObjectEvaluation("any-key", new Value(), new ImmutableContext())
        );
    }

    @TestFactory
    Iterable<DynamicTest> shouldEvaluateValuesCorrectly() {
        return Arrays.asList(
                evaluationTest(
                        "bool_true",
                        "bool_true",
                        "true",
                        provider -> provider.getBooleanEvaluation("bool_true", null, null),
                        evaluation -> evaluationChecks(evaluation, REASON_STATIC, true)
                ),
                evaluationTest(
                        "bool_false",
                        "bool_false",
                        "FaLsE",
                        provider -> provider.getBooleanEvaluation("bool_false", null, null),
                        evaluation -> evaluationChecks(evaluation, REASON_STATIC, false)
                ),
                evaluationTest(
                        "bool_false",
                        "bool_false",
                        "not-a-bool",
                        provider -> provider.getBooleanEvaluation("bool_false", null, null),
                        evaluation -> evaluationChecks(evaluation, REASON_STATIC, false)
                ),
                evaluationTest(
                        "string",
                        "string",
                        "value",
                        provider -> provider.getStringEvaluation("string", null, null),
                        evaluation -> evaluationChecks(evaluation, REASON_STATIC, "value")
                ),
                evaluationTest(
                        "int",
                        "INT",
                        "42",
                        provider -> provider.getIntegerEvaluation("INT", null, null),
                        evaluation -> evaluationChecks(evaluation, REASON_STATIC, 42)
                ),
                evaluationTest(
                        "double",
                        "double",
                        "42.0",
                        provider -> provider.getDoubleEvaluation("double", null, null),
                        evaluation -> evaluationChecks(evaluation, REASON_STATIC, 42.0)
                )
        );
    }

    @TestFactory
    Iterable<DynamicTest> shouldThrowFlagNotFoundOnMissingEnv() {
        return Arrays.asList(
                throwingEvaluationTest(
                        "bool_default",
                        "other",
                        "other",
                        provider -> provider.getBooleanEvaluation("bool_default", true, null),
                        FlagNotFoundError.class
                ),
                throwingEvaluationTest(
                        "string_default",
                        "other",
                        "other",
                        provider -> provider.getStringEvaluation("string_default", "value", null),
                        FlagNotFoundError.class
                ),
                throwingEvaluationTest(
                        "int_default",
                        "other",
                        "other",
                        provider -> provider.getIntegerEvaluation("int_default", 42, null),
                        FlagNotFoundError.class
                ),
                throwingEvaluationTest(
                        "double_default",
                        "other",
                        "other",
                        provider -> provider.getDoubleEvaluation("double_default", 42.0, null),
                        FlagNotFoundError.class
                ),
                throwingEvaluationTest(
                        "null_default",
                        "other",
                        "other",
                        provider -> provider.getStringEvaluation("null_default", null, null),
                        FlagNotFoundError.class
                )
        );
    }

    @TestFactory
    Iterable<DynamicTest> shouldThrowOnUnparseableValues() {
        return Arrays.asList(
                throwingEvaluationTest(
                        "int_incorrect",
                        "int_incorrect",
                        "fourty-two",
                        provider -> provider.getIntegerEvaluation("int_incorrect", null, null),
                        ParseError.class
                ),
                throwingEvaluationTest(
                        "double_incorrect",
                        "double_incorrect",
                        "fourty-two",
                        provider -> provider.getDoubleEvaluation("double_incorrect", null, null),
                        ParseError.class
                )
        );
    }

    @Test
    @DisplayName("should transform key if configured")
    void shouldTransformKeyIfConfigured() {
        String key = "key.transformed";
        String expected = "key_transformed";

        EnvironmentKeyTransformer transformer = EnvironmentKeyTransformer.replaceDotWithUnderscoreTransformer();
        EnvironmentGateway gateway = s -> s;
        EnvVarProvider provider = new EnvVarProvider(gateway, transformer);

        String environmentVariableValue = provider.getStringEvaluation(key, "failed", null).getValue();

        assertThat(environmentVariableValue).isEqualTo(expected);
    }

    @Test
    @DisplayName("should use passed-in EnvironmentGateway")
    void shouldUsePassedInEnvironmentGateway() {
        EnvironmentGateway testFake = s -> "true";

        EnvVarProvider provider = new EnvVarProvider(testFake);

        boolean actual = provider.getBooleanEvaluation("any key", false, null).getValue();

        assertThat(actual).isTrue();
    }

    private <T> DynamicTest evaluationTest(
            String testName,
            String variableName,
            String value,
            Function<FeatureProvider, ProviderEvaluation<T>> callback,
            Consumer<ProviderEvaluation<T>> checks
    ) {
        return DynamicTest.dynamicTest(
                testName,
                () -> {
                    // Given
                    final FeatureProvider provider = provider(variableName, value);

                    // When
                    final ProviderEvaluation<T> evaluation = callback.apply(provider);

                    // Then
                    checks.accept(evaluation);
                }
        );
    }

    private <T> DynamicTest throwingEvaluationTest(
            String testName,
            String variableName,
            String value,
            Function<FeatureProvider, ProviderEvaluation<T>> callback,
            Class<? extends Exception> throwing
    ) {
        return DynamicTest.dynamicTest(
                testName,
                () -> {
                    // Given
                    final FeatureProvider provider = provider(variableName, value);

                    // Then
                    assertThrows(throwing, () -> {
                        // When
                        callback.apply(provider);
                    });
                }
        );
    }

    private FeatureProvider provider(
            String variableName,
            String value
    ) {
        return new EnvVarProvider(name -> {
            if (name.equals(variableName)) {
                return value;
            } else {
                return null;
            }
        });
    }

    private <T> void evaluationChecks(ProviderEvaluation<T> evaluation, String reason, T expected) {
        assertEquals(reason, evaluation.getReason());
        assertEquals(expected, evaluation.getValue());
    }

    private static String REASON_STATIC = Reason.STATIC.toString();
    private static String REASON_DEFAULT = Reason.DEFAULT.toString();
}
