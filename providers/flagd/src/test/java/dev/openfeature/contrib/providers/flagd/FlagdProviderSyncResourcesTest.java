package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.exceptions.FatalError;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
        final AtomicLong waitTime = new AtomicLong(Long.MAX_VALUE);
        Thread waitingThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            isWaiting.set(true);
            flagdProviderSyncResources.waitForInitialization(10000);
            long end = System.currentTimeMillis();
            long duration = end - start;
            waitTime.set(duration);
        });
        waitingThread.start();

        while (!isWaiting.get()) {
            Thread.yield();
        }

        Thread.sleep(MAX_TIME_TOLERANCE); // waitingThread should have started waiting in the meantime

        flagdProviderSyncResources.initialize();

        waitingThread.join();

        var wait = MAX_TIME_TOLERANCE * 3;

        Assertions.assertTrue(
                waitTime.get() < wait,
                () -> "Wakeup should be almost instant, but took " + waitTime.get()
                        + " ms, which is more than the max of"
                        + wait + " ms");
    }

    @Timeout(2)
    @Test
    void callingShutdownWithPreviousNonFatal_wakesUpWaitingThread_WithGeneralException() throws InterruptedException {
        final AtomicBoolean isWaiting = new AtomicBoolean();
        final AtomicBoolean successfulTest = new AtomicBoolean();

        Thread waitingThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            isWaiting.set(true);
            Assertions.assertThrows(GeneralError.class, () -> flagdProviderSyncResources.waitForInitialization(10000));

            long end = System.currentTimeMillis();
            long duration = end - start;
            var wait = MAX_TIME_TOLERANCE * 3;
            successfulTest.set(duration < wait);
        });
        waitingThread.start();

        while (!isWaiting.get()) {
            Thread.yield();
        }

        Thread.sleep(MAX_TIME_TOLERANCE); // waitingThread should have started waiting in the meantime

        flagdProviderSyncResources.shutdown();

        waitingThread.join();

        Assertions.assertTrue(successfulTest.get());
    }

    @Timeout(2)
    @Test
    void callingShutdownWithPreviousFatal_wakesUpWaitingThread_WithFatalException() throws InterruptedException {
        final AtomicBoolean isWaiting = new AtomicBoolean();
        final AtomicBoolean successfulTest = new AtomicBoolean();
        flagdProviderSyncResources.fatalError(null);

        Thread waitingThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            isWaiting.set(true);
            Assertions.assertThrows(FatalError.class, () -> flagdProviderSyncResources.waitForInitialization(10000));

            long end = System.currentTimeMillis();
            long duration = end - start;
            var wait = MAX_TIME_TOLERANCE * 3;
            successfulTest.set(duration < wait);
        });
        waitingThread.start();

        while (!isWaiting.get()) {
            Thread.yield();
        }

        Thread.sleep(MAX_TIME_TOLERANCE); // waitingThread should have started waiting in the meantime

        flagdProviderSyncResources.shutdown();

        waitingThread.join();

        Assertions.assertTrue(successfulTest.get());
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

    @Timeout(2)
    @Test
    void fatalHasPrecedenceOverInitAndShutdown() {
        flagdProviderSyncResources.fatalError(null);
        flagdProviderSyncResources.initialize();
        flagdProviderSyncResources.shutdown();

        Assertions.assertThrows(FatalError.class, () -> flagdProviderSyncResources.waitForInitialization(10000));
    }

    @Timeout(2)
    @Test
    void fatalAbortsInit() throws InterruptedException {
        final AtomicBoolean isWaiting = new AtomicBoolean();
        final AtomicLong waitTime = new AtomicLong(Long.MAX_VALUE);
        final AtomicReference<Exception> fatalException = new AtomicReference<>();

        Thread waitingThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            isWaiting.set(true);
            try {
                flagdProviderSyncResources.waitForInitialization(10000);
            } catch (Exception e) {
                fatalException.set(e);
            }
            long end = System.currentTimeMillis();
            long duration = end - start;
            waitTime.set(duration);
        });
        waitingThread.start();

        while (!isWaiting.get()) {
            Thread.yield();
        }

        Thread.sleep(MAX_TIME_TOLERANCE); // waitingThread should have started waiting in the meantime

        var fatalEvent = ProviderEventDetails.builder()
                .errorCode(ErrorCode.PROVIDER_FATAL)
                .message("Some message")
                .build();
        flagdProviderSyncResources.fatalError(fatalEvent);

        waitingThread.join();

        var wait = MAX_TIME_TOLERANCE * 3;

        Assertions.assertTrue(
                waitTime.get() < wait,
                () -> "Wakeup should be almost instant, but took " + waitTime.get()
                        + " ms, which is more than the max of"
                        + wait + " ms");
        Assertions.assertNotNull(fatalException.get());
        Assertions.assertInstanceOf(FatalError.class, fatalException.get());
        Assertions.assertEquals(
                "Initialization failed due to a fatal error: " + fatalEvent.getMessage(),
                fatalException.get().getMessage());
    }
}
