package dev.openfeature.contrib.hooks.codereadiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SemVerComparatorTest {

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
        SemVerComparator comparator = new SemVerComparator();
        boolean result = comparator.compare(comparator.parse(currentVersion), comparator.parse(minCodeVersion));
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when version is invalid semver in parse")
    void testInvalidVersion() {
        SemVerComparator comparator = new SemVerComparator();
        assertThatThrownBy(() -> comparator.parse("invalid-version"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid semver");
    }

    @Test
    @DisplayName("Should throw NullPointerException when versionString is null in parse")
    void testNullVersionString() {
        SemVerComparator comparator = new SemVerComparator();
        assertThatThrownBy(() -> comparator.parse(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("versionString cannot be null");
    }

    @Test
    @DisplayName("Should throw NullPointerException when arguments are null in compare")
    void testNullArgumentsInCompare() throws Exception {
        SemVerComparator comparator = new SemVerComparator();
        org.semver4j.Semver valid = comparator.parse("1.0.0");
        assertThatThrownBy(() -> comparator.compare(null, valid))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currentVersion cannot be null");
        assertThatThrownBy(() -> comparator.compare(valid, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("minCodeVersion cannot be null");
    }
}
