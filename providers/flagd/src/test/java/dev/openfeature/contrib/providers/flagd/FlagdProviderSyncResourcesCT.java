package dev.openfeature.contrib.providers.flagd;

import com.vmlens.api.AllInterleavings;
import com.vmlens.api.Runner;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class FlagdProviderSyncResourcesCT {
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

    @Timeout(5)
    @Test
    void callingInitialize_wakesUpWaitingThread() {
        try (var interleavings = new AllInterleavings("calling initialize() wakes up waiting thread")) {
            while (interleavings.hasNext()) {
                final var startTime = new AtomicLong();
                final var endTime = new AtomicLong();
                Runner.runParallel(
                        () -> {
                            flagdProviderSyncResources.waitForInitialization(10000);
                            endTime.set(System.currentTimeMillis());
                            Assertions.assertTrue(flagdProviderSyncResources.isInitialized());
                        },
                        () -> {
                            startTime.set(System.currentTimeMillis());
                            flagdProviderSyncResources.initialize();
                        }
                );

                Assertions.assertTrue(endTime.get() - startTime.get() <= MAX_TIME_TOLERANCE, () ->
                        "Expected waiting thread to be released shortly after initialization, but waited for "
                                + (endTime.get() - startTime.get()) + "ms"
                );
            }
        }
    }

    @Timeout(5)
    @Test
    void callingShutdown_wakesUpWaitingThreadWithException() {
        try (var interleavings = new AllInterleavings("calling shutdown() wakes up waiting thread with exception")) {
            while (interleavings.hasNext()) {
                final var startTime = new AtomicLong();
                final var endTime = new AtomicLong();
                Runner.runParallel(
                        () -> {
                            Assertions.assertThrows(
                                    IllegalStateException.class,
                                    () -> flagdProviderSyncResources.waitForInitialization(10000));
                            Assertions.assertFalse(flagdProviderSyncResources.isInitialized());
                            Assertions.assertTrue(flagdProviderSyncResources.isShutDown());
                        },
                        () -> {
                            startTime.set(System.currentTimeMillis());
                            flagdProviderSyncResources.shutdown();
                        }
                );

                Assertions.assertTrue(endTime.get() - startTime.get() <= MAX_TIME_TOLERANCE, () ->
                        "Expected waiting thread to be released shortly after initialization, but waited for "
                                + (endTime.get() - startTime.get()) + "ms"
                );
            }
        }
    }

    @Timeout(5)
    @Test
    void concurrentInitializesWork() {
        try (var interleavings = new AllInterleavings("concurrent initialize() calls work")) {
            while (interleavings.hasNext()) {
                Runner.runParallel(
                        () -> flagdProviderSyncResources.initialize(),
                        () -> flagdProviderSyncResources.initialize()
                );
                Assertions.assertTrue(flagdProviderSyncResources.isInitialized());
            }
        }
    }

    @Timeout(5)
    @Test
    void concurrentInitializeAndShutdownShutsDownWork() {
        try (var interleavings = new AllInterleavings("concurrent initialize() calls work")) {
            while (interleavings.hasNext()) {
                Runner.runParallel(
                        () -> flagdProviderSyncResources.initialize(),
                        () -> flagdProviderSyncResources.shutdown()
                );
                Assertions.assertFalse(flagdProviderSyncResources.isInitialized());
                Assertions.assertTrue(flagdProviderSyncResources.isShutDown());
            }
        }
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
