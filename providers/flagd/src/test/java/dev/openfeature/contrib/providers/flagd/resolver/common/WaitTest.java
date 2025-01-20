package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class WaitTest {
    private static final long PERMISSIBLE_EPSILON = 20;

    @Timeout(2)
    @Test
    void waitUntilFinished_failsWhenDeadlineElapses() {
        final Wait wait = new Wait();
        Assertions.assertThrows(GeneralError.class, () -> wait.waitUntilFinished(10));
    }

    @Timeout(2)
    @Test
    void waitUntilFinished_WaitsApproxForDeadline() {
        final Wait wait = new Wait();
        final AtomicLong start = new AtomicLong();
        final AtomicLong end = new AtomicLong();
        final long deadline = 45;
        Assertions.assertThrows(GeneralError.class, () -> {
            start.set(System.currentTimeMillis());
            try {
                wait.waitUntilFinished(deadline);
            } catch (Exception e) {
                end.set(System.currentTimeMillis());
                throw e;
            }
        });
        final long elapsed = end.get() - start.get();
        // should wait at least for the deadline
        Assertions.assertTrue(elapsed >= deadline);
        // should not wait much longer than the deadline
        Assertions.assertTrue(elapsed < deadline + PERMISSIBLE_EPSILON);
    }

    @Timeout(2)
    @Test
    void interruptingWaitingThread_isIgnored() throws InterruptedException {
        final AtomicBoolean isWaiting = new AtomicBoolean();
        final Wait wait = new Wait();
        final long deadline = 500;
        Thread t0 = new Thread(() -> {
            long start = System.currentTimeMillis();
            isWaiting.set(true);
            wait.waitUntilFinished(deadline);
            long end = System.currentTimeMillis();
            long duration = end - start;
            // even though thread was interrupted, it still waited for the deadline
            Assertions.assertTrue(duration >= deadline);
            Assertions.assertTrue(duration < deadline + PERMISSIBLE_EPSILON);
        });
        t0.start();

        while (!isWaiting.get()) {
            Thread.yield();
        }

        Thread.sleep(10); // t0 should have started waiting in the meantime

        for (int i = 0; i < 50; i++) {
            t0.interrupt();
            Thread.sleep(10);
        }

        t0.join();
    }

    @Timeout(2)
    @Test
    void callingOnFinished_wakesUpWaitingThread() throws InterruptedException {
        final AtomicBoolean isWaiting = new AtomicBoolean();
        final Wait wait = new Wait();
        Thread t0 = new Thread(() -> {
            long start = System.currentTimeMillis();
            isWaiting.set(true);
            wait.waitUntilFinished(10000);
            long end = System.currentTimeMillis();
            long duration = end - start;
            Assertions.assertTrue(duration < PERMISSIBLE_EPSILON);
        });
        t0.start();

        while (!isWaiting.get()) {
            Thread.yield();
        }

        Thread.sleep(10); // t0 should have started waiting in the meantime

        wait.onFinished();

        t0.join();
    }

    @Timeout(2)
    @Test
    void waitingOnFinished_returnsInstantly() {
        Wait finished = Wait.finished();
        long start = System.currentTimeMillis();
        finished.waitUntilFinished(10000);
        long end = System.currentTimeMillis();
        // do not use PERMISSIBLE_EPSILON here, this should happen faster than that
        Assertions.assertTrue(start + 2 > end);
    }
}
