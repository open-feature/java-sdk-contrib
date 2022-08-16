package dev.openfeature.contrib.providers.flagd;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlagdProviderTest {

    @Test
    @DisplayName("a simple test")
    void test() {
        assertThat(FlagdProvider.test()).isEqualTo(true);
    }
}
