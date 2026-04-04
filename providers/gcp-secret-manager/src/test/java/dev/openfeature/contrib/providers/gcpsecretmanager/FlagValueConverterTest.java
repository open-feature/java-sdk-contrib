package dev.openfeature.contrib.providers.gcpsecretmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.ParseError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("FlagValueConverter")
class FlagValueConverterTest {

    @Nested
    @DisplayName("Boolean conversion")
    class BooleanConversion {

        @ParameterizedTest
        @ValueSource(strings = {"true", "True", "TRUE", "tRuE"})
        @DisplayName("converts truthy strings to true")
        void trueVariants(String input) {
            assertThat(FlagValueConverter.convert(input, Boolean.class)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"false", "False", "FALSE", "fAlSe"})
        @DisplayName("converts falsy strings to false")
        void falseVariants(String input) {
            assertThat(FlagValueConverter.convert(input, Boolean.class)).isFalse();
        }

        @Test
        @DisplayName("throws ParseError for non-boolean string")
        void nonBooleanThrows() {
            assertThatThrownBy(() -> FlagValueConverter.convert("yes", Boolean.class))
                    .isInstanceOf(ParseError.class);
        }
    }

    @Nested
    @DisplayName("Integer conversion")
    class IntegerConversion {

        @Test
        @DisplayName("converts numeric string to Integer")
        void numericString() {
            assertThat(FlagValueConverter.convert("42", Integer.class)).isEqualTo(42);
        }

        @Test
        @DisplayName("throws ParseError for non-numeric string")
        void nonNumericThrows() {
            assertThatThrownBy(() -> FlagValueConverter.convert("abc", Integer.class))
                    .isInstanceOf(ParseError.class);
        }
    }

    @Nested
    @DisplayName("Double conversion")
    class DoubleConversion {

        @Test
        @DisplayName("converts numeric string to Double")
        void numericString() {
            assertThat(FlagValueConverter.convert("3.14", Double.class)).isEqualTo(3.14);
        }

        @Test
        @DisplayName("throws ParseError for non-numeric string")
        void nonNumericThrows() {
            assertThatThrownBy(() -> FlagValueConverter.convert("not-a-number", Double.class))
                    .isInstanceOf(ParseError.class);
        }
    }

    @Nested
    @DisplayName("String conversion")
    class StringConversion {

        @Test
        @DisplayName("returns string as-is")
        void returnsAsIs() {
            assertThat(FlagValueConverter.convert("dark-mode", String.class)).isEqualTo("dark-mode");
        }
    }

    @Nested
    @DisplayName("Value (object) conversion")
    class ValueConversion {

        @Test
        @DisplayName("parses JSON object string to Value with Structure")
        void jsonObject() {
            Value value = FlagValueConverter.convert("{\"color\":\"blue\",\"count\":3}", Value.class);
            assertThat(value).isNotNull();
            assertThat(value.asStructure()).isNotNull();
            assertThat(value.asStructure().getValue("color").asString()).isEqualTo("blue");
            assertThat(value.asStructure().getValue("count").asInteger()).isEqualTo(3);
        }

        @Test
        @DisplayName("parses JSON array string to Value with List")
        void jsonArray() {
            Value value = FlagValueConverter.convert("[\"a\",\"b\"]", Value.class);
            assertThat(value).isNotNull();
            assertThat(value.asList()).hasSize(2);
        }

        @Test
        @DisplayName("wraps plain string in Value")
        void plainStringWrapped() {
            Value value = FlagValueConverter.convert("plain", Value.class);
            assertThat(value.asString()).isEqualTo("plain");
        }
    }

    @Test
    @DisplayName("throws TypeMismatchError for unsupported type")
    void unsupportedTypeThrows() {
        assertThatThrownBy(() -> FlagValueConverter.convert("foo", Object.class))
                .isInstanceOf(TypeMismatchError.class);
    }

    @Test
    @DisplayName("throws ParseError when raw value is null")
    void nullRawThrows() {
        assertThatThrownBy(() -> FlagValueConverter.convert(null, Boolean.class))
                .isInstanceOf(ParseError.class);
    }
}
