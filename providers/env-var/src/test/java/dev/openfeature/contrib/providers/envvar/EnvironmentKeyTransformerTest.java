package dev.openfeature.contrib.providers.envvar;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EnvironmentKeyTransformerTest {

    private final EnvironmentGateway actualGateway = s -> s;

    @ParameterizedTest
    @CsvSource({
        ", ",
        "a, a",
        "A, a",
        "ABC_DEF_GHI, abc_def_ghi",
        "ABC.DEF.GHI, abc.def.ghi",
        "aBc, abc"
    })
    @DisplayName("should transform keys to lower case prior delegating call to actual gateway")
    void shouldTransformKeysToLowerCasePriorDelegatingCallToActualGateway(String originalKey, String expectedTransformedKey) {
        String actual = EnvironmentKeyTransformer
            .toLowerCaseTransformer()
            .withSource(actualGateway)
            .getEnvironmentVariable(originalKey);

        assertThat(actual).isEqualTo(expectedTransformedKey);
    }

    @ParameterizedTest
    @CsvSource({
        ", ",
        "a, a",
        "A, a",
        "ABC_DEF_GHI, abcDefGhi",
        "ABC.DEF.GHI, abc.def.ghi",
        "aBc, abc"
    })
    @DisplayName("should transform keys to camel case prior delegating call to actual gateway")
    void shouldTransformKeysToCamelCasePriorDelegatingCallToActualGateway(String originalKey, String expectedTransformedKey) {
        EnvironmentGateway transformingGateway = EnvironmentKeyTransformer
            .toCamelCaseTransformer()
            .withSource(actualGateway);
        String actual = transformingGateway.getEnvironmentVariable(originalKey);

        assertThat(actual).isEqualTo(expectedTransformedKey);
    }

    @ParameterizedTest
    @CsvSource({
        ", ",
        "a, A",
        "A, A",
        "ABC_DEF_GHI, ABC_DEF_GHI",
        "ABC.DEF.GHI, ABC.DEF.GHI",
        "aBc_abc, ABC_ABC"
    })
    @DisplayName("should transform keys according to given transformation prior delegating call to actual gateway")
    void shouldTransformKeysAccordingToGivenPriorDelegatingCallToActualGateway(String originalKey, String expectedTransformedKey) {
        EnvironmentGateway transformingGateway = new EnvironmentKeyTransformer(StringUtils::toRootUpperCase)
            .withSource(actualGateway);

        String actual = transformingGateway.getEnvironmentVariable(originalKey);

        assertThat(actual).isEqualTo(expectedTransformedKey);
    }

    @ParameterizedTest
    @CsvSource({
        "ABC_DEF_GHI, abc.def.ghi",
        "ABC.DEF.GHI, abc.def.ghi",
        "aBc, abc"
    })
    @DisplayName("should compose transformers")
    void shouldComposeTransformers(String originalKey, String expectedTransformedKey) {
        EnvironmentGateway transformingGateway = EnvironmentKeyTransformer
            .toLowerCaseTransformer()
            .andThen(EnvironmentKeyTransformer.replaceUnderscoreWithDotTransformer())
            .withSource(actualGateway);

        String actual = transformingGateway.getEnvironmentVariable(originalKey);

        assertThat(actual).isEqualTo(expectedTransformedKey);
    }

    @ParameterizedTest
    @CsvSource({
        "abc-def-ghi, ABC_DEF_GHI",
        "abc.def.ghi, ABC.DEF.GHI",
        "abc, ABC"
    })
    @DisplayName("should support screaming snake to hyphen case keys")
    void shouldSupportScreamingSnakeToHyphenCaseKeys(String originalKey, String expectedTransformedKey) {
        EnvironmentGateway transformingGateway = EnvironmentKeyTransformer
            .hyphenCaseToScreamingSnake()
            .withSource(actualGateway);

        String actual = transformingGateway.getEnvironmentVariable(originalKey);

        assertThat(actual).isEqualTo(expectedTransformedKey);
    }

    @ParameterizedTest
    @CsvSource({
        "abc-def-ghi, abc-def-ghi",
        "abc.def.ghi, abc_def_ghi",
        "abc, abc"
    })
    @DisplayName("should support replacing dot with underscore")
    void shouldSupportReplacingDotWithUnderscore(String originalKey, String expectedTransformedKey) {
        EnvironmentGateway transformingGateway = EnvironmentKeyTransformer
            .replaceDotWithUnderscoreTransformer()
            .withSource(actualGateway);

        String actual = transformingGateway.getEnvironmentVariable(originalKey);

        assertThat(actual).isEqualTo(expectedTransformedKey);
    }

}
