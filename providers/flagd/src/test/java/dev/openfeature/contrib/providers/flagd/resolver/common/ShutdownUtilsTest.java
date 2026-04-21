package dev.openfeature.contrib.providers.flagd.resolver.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ShutdownUtilsTest {

    @Test
    void doesNotThrowWhenActionSucceeds() {
        assertDoesNotThrow(() -> ShutdownUtils.awaitTerminationQuietly(() -> {}));
    }

    @Test
    void suppressesInterruptedExceptionAndRestoresFlag() {
        Thread.interrupted(); // clear flag

        ShutdownUtils.awaitTerminationQuietly(() -> {
            throw new InterruptedException();
        });

        assertTrue(Thread.interrupted(), "interrupt flag should be restored");
    }
}
