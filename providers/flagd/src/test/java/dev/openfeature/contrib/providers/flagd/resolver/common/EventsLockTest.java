package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.contrib.providers.flagd.EventsLock;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class EventsLockTest {
    private static final long PERMISSIBLE_EPSILON = 20;

    private EventsLock eventsLock;

    @BeforeEach
    void setUp() {
        eventsLock = new EventsLock();
    }

    @Timeout(2)
    @Test
    void waitForInitialization_failsWhenDeadlineElapses() {
        Assertions.assertThrows(GeneralError.class, () -> eventsLock.waitForInitialization(2));
    }

    @Timeout(2)
    @Test
    void waitForInitialization_waitsApproxForDeadline() {
        final AtomicLong start = new AtomicLong();
        final AtomicLong end = new AtomicLong();
        final long deadline = 45;
        Assertions.assertThrows(GeneralError.class, () -> {
            start.set(System.currentTimeMillis());
            try {
                eventsLock.waitForInitialization(deadline);
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
        final long deadline = 500;
        Thread waitingThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            isWaiting.set(true);
            eventsLock.waitForInitialization(deadline);
            long end = System.currentTimeMillis();
            long duration = end - start;
            // even though thread was interrupted, it still waited for the deadline
            Assertions.assertTrue(duration >= deadline);
            Assertions.assertTrue(duration < deadline + PERMISSIBLE_EPSILON);
        });
        waitingThread.start();

        while (!isWaiting.get()) {
            Thread.yield();
        }

        Thread.sleep(PERMISSIBLE_EPSILON); // waitingThread should have started waiting in the meantime

        for (int i = 0; i < 50; i++) {
            waitingThread.interrupt();
            Thread.sleep(10);
        }

        waitingThread.join();
    }

    @Timeout(2)
    @Test
    void callingInitialize_wakesUpWaitingThread() throws InterruptedException {
        final AtomicBoolean isWaiting = new AtomicBoolean();
        Thread waitingThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            isWaiting.set(true);
            eventsLock.waitForInitialization(10000);
            long end = System.currentTimeMillis();
            long duration = end - start;
            Assertions.assertTrue(duration < PERMISSIBLE_EPSILON);
        });
        waitingThread.start();

        while (!isWaiting.get()) {
            Thread.yield();
        }

        Thread.sleep(PERMISSIBLE_EPSILON); // waitingThread should have started waiting in the meantime

        eventsLock.initialize();

        waitingThread.join();
    }

    @Timeout(2)
    @Test
    void callingShutdown_wakesUpWaitingThreadWithException() throws InterruptedException {
        final AtomicBoolean isWaiting = new AtomicBoolean();
        Thread waitingThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            isWaiting.set(true);
            Assertions.assertThrows(IllegalArgumentException.class, () -> eventsLock.waitForInitialization(10000));

            long end = System.currentTimeMillis();
            long duration = end - start;
            Assertions.assertTrue(duration < PERMISSIBLE_EPSILON);
        });
        waitingThread.start();

        while (!isWaiting.get()) {
            Thread.yield();
        }

        Thread.sleep(PERMISSIBLE_EPSILON); // waitingThread should have started waiting in the meantime

        eventsLock.shutdown();

        waitingThread.join();
    }

    @Timeout(2)
    @Test
    void waitForInitializationAfterCallingInitialize_returnsInstantly() {
        eventsLock.initialize();
        long start = System.currentTimeMillis();
        eventsLock.waitForInitialization(10000);
        long end = System.currentTimeMillis();
        // do not use PERMISSIBLE_EPSILON here, this should happen faster than that
        Assertions.assertTrue(start + 1 >= end);
    }
}
