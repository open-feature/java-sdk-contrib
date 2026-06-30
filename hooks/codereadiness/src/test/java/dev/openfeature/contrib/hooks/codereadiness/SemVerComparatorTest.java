package dev.openfeature.contrib.hooks.codereadiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SemVerComparatorTest {

    private final SemVerComparator comparator = new SemVerComparator();

    @ParameterizedTest
    @CsvSource({
        "1.2.0, 1.1.0, true",
        "1.1.0, 1.1.0, true",
        "v1.2.0, 1.1.0, true",
        "1.2.0, v1.1.0, true",
        "v1.2.0, v1.1.0, true",
        "2.0.0, 1.9.9, true",
        "1.0.0, 1.1.0, false",
        "v1.0.0, v1.1.0, false"
    })
    @DisplayName("Should validate versions correctly according to SemVer rules")
    void testVersionComparison(String currentVersion, String minCodeVersion, boolean expectedResult) throws Exception {
        boolean result = comparator.compare(currentVersion, minCodeVersion);
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when currentVersion is invalid semver")
    void testInvalidCurrentVersion() {
        assertThatThrownBy(() -> comparator.compare("invalid-version", "1.0.0"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid current semver");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when minCodeVersion is invalid semver")
    void testInvalidMinCodeVersion() {
        assertThatThrownBy(() -> comparator.compare("1.0.0", "invalid-version"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid min code version semver");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when currentVersion is null")
    void testNullCurrentVersion() {
        assertThatThrownBy(() -> comparator.compare(null, "1.0.0"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when minCodeVersion is null")
    void testNullMinCodeVersion() {
        assertThatThrownBy(() -> comparator.compare("1.0.0", null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
