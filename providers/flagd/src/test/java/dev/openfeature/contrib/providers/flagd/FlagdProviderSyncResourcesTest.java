package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class FlagdProviderSyncResourcesTest {
    private static final long MAX_TIME_TOLERANCE = 20;

    private FlagdProviderSyncResources flagdProviderSyncResources;

    @BeforeEach
    void setUp() {
        flagdProviderSyncResources = new FlagdProviderSyncResources();
    }

    @Timeout(2)
    @Test
    void waitForInitialization_failsWhenDeadlineElapses() {
        Assertions.assertThrows(GeneralError.class, () -> flagdProviderSyncResources.waitForInitialization(2));
    }

    @Timeout(2)
    @Test
    void waitForInitialization_waitsApproxForDeadline() {
        final AtomicLong start = new AtomicLong();
        final AtomicLong end = new AtomicLong();
        final long deadline = 45;

        start.set(System.currentTimeMillis());
        Assertions.assertThrows(GeneralError.class, () -> flagdProviderSyncResources.waitForInitialization(deadline));
        end.set(System.currentTimeMillis());

        final long elapsed = end.get() - start.get();
        // should wait at least for the deadline
        Assertions.assertTrue(elapsed >= deadline);
        // should not wait much longer than the deadline
        Assertions.assertTrue(elapsed < deadline + MAX_TIME_TOLERANCE);
    }

    @Timeout(2)
    @Test
    void interruptingWaitingThread_isIgnored() throws InterruptedException {
        final AtomicBoolean isWaiting = new AtomicBoolean();
        final long deadline = 500;
        Thread waitingThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            isWaiting.set(true);
            Assertions.assertThrows(
                    GeneralError.class, () -> flagdProviderSyncResources.waitForInitialization(deadline));

            long end = System.currentTimeMillis();
            long duration = end - start;
            // even though thread was interrupted, it still waited for the deadline
            Assertions.assertTrue(duration >= deadline);
            Assertions.assertTrue(duration < deadline + MAX_TIME_TOLERANCE);
        });
        waitingThread.start();

        while (!isWaiting.get()) {
            Thread.yield();
        }

        Thread.sleep(MAX_TIME_TOLERANCE); // waitingThread should have started waiting in the meantime

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
            flagdProviderSyncResources.waitForInitialization(10000);
            long end = System.currentTimeMillis();
            long duration = end - start;
            Assertions.assertTrue(duration < MAX_TIME_TOLERANCE);
        });
        waitingThread.start();

        while (!isWaiting.get()) {
            Thread.yield();
        }

        Thread.sleep(MAX_TIME_TOLERANCE); // waitingThread should have started waiting in the meantime

        flagdProviderSyncResources.initialize();

        waitingThread.join();
    }

    @Timeout(2)
    @Test
    void callingShutdown_wakesUpWaitingThreadWithException() throws InterruptedException {
        final AtomicBoolean isWaiting = new AtomicBoolean();
        Thread waitingThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            isWaiting.set(true);
            Assertions.assertThrows(
                    IllegalStateException.class, () -> flagdProviderSyncResources.waitForInitialization(10000));

            long end = System.currentTimeMillis();
            long duration = end - start;
            Assertions.assertTrue(duration < MAX_TIME_TOLERANCE);
        });
        waitingThread.start();

        while (!isWaiting.get()) {
            Thread.yield();
        }

        Thread.sleep(MAX_TIME_TOLERANCE); // waitingThread should have started waiting in the meantime

        flagdProviderSyncResources.shutdown();

        waitingThread.join();
    }

    @Timeout(2)
    @Test
    void waitForInitializationAfterCallingInitialize_returnsInstantly() {
        flagdProviderSyncResources.initialize();
        long start = System.currentTimeMillis();
        flagdProviderSyncResources.waitForInitialization(10000);
        long end = System.currentTimeMillis();
        // do not use MAX_TIME_TOLERANCE here, this should happen faster than that
        Assertions.assertTrue(start + 1 >= end);
    }
}
