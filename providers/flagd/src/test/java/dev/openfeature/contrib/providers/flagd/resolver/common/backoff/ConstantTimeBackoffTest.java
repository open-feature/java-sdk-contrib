package dev.openfeature.contrib.providers.flagd.resolver.common.backoff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ConstantTimeBackoffTest {
    @Test
    void isNotExhaustedInitially() {
        ConstantTimeBackoff constantTimeBackoff = new ConstantTimeBackoff(1000);
        assertFalse(constantTimeBackoff.isExhausted());
    }

    @Test
    void isNotExhaustedAfterNextBackoff() {
        ConstantTimeBackoff constantTimeBackoff = new ConstantTimeBackoff(1000);
        constantTimeBackoff.nextBackoff();
        assertFalse(constantTimeBackoff.isExhausted());
    }

    @Test
    void getCurrentBackoffMillis() {
        ConstantTimeBackoff constantTimeBackoff = new ConstantTimeBackoff(1000);
        assertEquals(1000, constantTimeBackoff.getCurrentBackoffMillis());
    }

    @Test
    void nextBackoffDoesNotChangeCurrentValue() {
        ConstantTimeBackoff constantTimeBackoff = new ConstantTimeBackoff(1000);
        constantTimeBackoff.nextBackoff();
        assertEquals(1000, constantTimeBackoff.getCurrentBackoffMillis());
    }

    @Test
    void resetDoesNotChangeCurrentValue() {
        ConstantTimeBackoff constantTimeBackoff = new ConstantTimeBackoff(1000);
        constantTimeBackoff.nextBackoff();
        constantTimeBackoff.reset();
        assertEquals(1000, constantTimeBackoff.getCurrentBackoffMillis());
    }

    @Test
    void resetDoesNotChangeIsExhausted() {
        ConstantTimeBackoff constantTimeBackoff = new ConstantTimeBackoff(1000);
        constantTimeBackoff.nextBackoff();
        constantTimeBackoff.reset();
        assertFalse(constantTimeBackoff.isExhausted());
    }
}
