package dev.openfeature.contrib.providers.flagd.resolver.common.backoff;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExponentialTimeBackoffTest {
    @Test
    void isNotExhaustedInitially() {
        ExponentialTimeBackoff target = new ExponentialTimeBackoff(1000);
        assertFalse(target.isExhausted());
    }

    @Test
    void isNotExhaustedAfterNextBackoff() {
        ExponentialTimeBackoff target = new ExponentialTimeBackoff(1000);
        target.nextBackoff();
        assertFalse(target.isExhausted());
    }

    @Test
    void getCurrentBackoffMillis() {
        ExponentialTimeBackoff target = new ExponentialTimeBackoff(1000);
        assertEquals(1000, target.getCurrentBackoffMillis());
    }

    @ParameterizedTest(name = "{0} times backoff")
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void backoffIncreasesExponentially(int iteration) {
        ExponentialTimeBackoff target = new ExponentialTimeBackoff(2000, Long.MAX_VALUE);
        for (int i = 0; i < iteration; i++) {
            target.nextBackoff();
        }

        long expectedValue = ((long) Math.pow(2, iteration)) * 2000;
        assertEquals(expectedValue, target.getCurrentBackoffMillis());
    }

    @Test
    void getCurrentBackoffMillisDoesNotIncreaseBeyondMaxBackoff() {
        ExponentialTimeBackoff target = new ExponentialTimeBackoff(1000, 5000);

        target.nextBackoff(); // 2000
        target.nextBackoff(); // 4000
        target.nextBackoff(); // 5000 (8000)

        assertEquals(5000, target.getCurrentBackoffMillis());
    }

    @Test
    void maxDefaultBackoffIsDefinedWhenNoBoundaryIsSet() {
        ExponentialTimeBackoff target = new ExponentialTimeBackoff(1000);

        // ~7 iterations == 128000; DEFAULT_MAX_BACK_OFF == 120000
        for (int i = 0; i < 7; i++) {
            target.nextBackoff();
        }

        assertEquals(ExponentialTimeBackoff.DEFAULT_MAX_BACK_OFF, target.getCurrentBackoffMillis());
    }

    @Test
    void resetResetsToInitialValue() {
        ExponentialTimeBackoff target = new ExponentialTimeBackoff(1000);
        target.nextBackoff();
        target.reset();
        assertEquals(1000, target.getCurrentBackoffMillis());
    }

    @Test
    void resetDoesNotChangeIsExhausted() {
        ExponentialTimeBackoff target = new ExponentialTimeBackoff(1000);
        target.nextBackoff();
        target.reset();
        assertFalse(target.isExhausted());
    }
}
