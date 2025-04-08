package dev.openfeature.contrib.providers.flagd.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Concurrent / Concurrency utilities.
 *
 * @author Liran Mendelovich
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ConcurrentUtils {

    /**
     * Graceful shutdown a thread pool. <br>
     * See <a
     * href="https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html">
     * https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html</a>
     *
     * @param pool thread pool
     * @param timeoutSeconds grace period timeout in seconds - timeout can be twice than this value,
     *     as first it waits for existing tasks to terminate, then waits for cancelled tasks to
     *     terminate.
     */
    public static void shutdownAndAwaitTermination(ExecutorService pool, int timeoutSeconds) {
        if (pool == null) {
            return;
        }

        // Disable new tasks from being submitted
        pool.shutdown();
        try {

            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {

                // Cancel currently executing tasks - best effort, based on interrupt handling
                // implementation.
                pool.shutdownNow();

                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                    log.error("Thread pool did not shutdown all tasks after the timeout: {} seconds.", timeoutSeconds);
                }
            }
        } catch (InterruptedException e) {

            log.info("Current thread interrupted during shutdownAndAwaitTermination, calling shutdownNow.");

            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();

            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }
}
