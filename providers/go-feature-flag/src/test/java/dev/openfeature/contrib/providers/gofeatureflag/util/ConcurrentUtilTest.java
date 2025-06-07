package dev.openfeature.contrib.providers.gofeatureflag.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class ConcurrentUtilTest {
    @Test
    void testShutdownAndAwaitTermination_NormalShutdown() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                Thread.sleep(100); // Simulate a short task
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ConcurrentUtil.shutdownAndAwaitTermination(executor, 1);

        assertTrue(executor.isShutdown());
    }

    @Test
    void testShutdownAndAwaitTermination_ForcedShutdown() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                Thread.sleep(5000); // Simulate a long-running task
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ConcurrentUtil.shutdownAndAwaitTermination(executor, 1);

        assertTrue(executor.isShutdown());
    }

    @Test
    void testShutdownAndAwaitTermination_InterruptedException() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Thread.currentThread().interrupt(); // Simulate an interruption

        ConcurrentUtil.shutdownAndAwaitTermination(executor, 1);

        assertTrue(Thread.interrupted()); // Verify the interrupt status is preserved
        assertTrue(executor.isShutdown());
    }
}
