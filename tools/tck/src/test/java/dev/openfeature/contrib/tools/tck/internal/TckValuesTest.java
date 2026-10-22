package dev.openfeature.contrib.tools.tck.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The type vocabulary feature files write values in. */
class TckValuesTest {

    @Test
    @DisplayName("Long and StringList are the types a context table reaches for")
    void theWiderTypes() {
        assertThat(TckValues.convert("4294967296", "Long")).isEqualTo(4294967296L);
        assertThat(TckValues.convert("a, b ,c", "StringList")).isEqualTo(List.of("a", "b", "c"));
        assertThat(TckValues.convert("", "StringList")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("a flag's Integer default stays bounded by the accessor that reads it back")
    void aFlagDefaultIsStillRejectedWhenItDoesNotFit() {
        assertThatThrownBy(() -> TckValues.convert("4294967295", "Integer"))
                .as("getIntegerDetails cannot ask for this, so a feature file asking is a mistake")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a 32-bit Integer");
    }

    @Test
    @DisplayName("the same value in a context entry widens instead, because nothing reads it back")
    void aContextEntryWidens() {
        assertThat(TckValues.convertContextEntry("4294967295", "Integer"))
                .as("a targeting operator may hash a 64-bit number")
                .isEqualTo(4294967295L);
        assertThat(TckValues.convertContextEntry("7", "Integer"))
                .as("what fits is still an Integer, so a provider sees the narrower type")
                .isEqualTo(7);
    }

    @Test
    @DisplayName("every other type converts the same either way")
    void theRestIsUnchanged() {
        for (String[] pair : new String[][] {{"true", "Boolean"}, {"x", "String"}, {"1.5", "Float"}}) {
            assertThat(TckValues.convertContextEntry(pair[0], pair[1])).isEqualTo(TckValues.convert(pair[0], pair[1]));
        }
        assertThat(TckValues.convertContextEntry("null", "Integer")).isNull();
    }
}
