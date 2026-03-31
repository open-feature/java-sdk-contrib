package dev.openfeature.contrib.providers.flagd.e2e;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.extern.slf4j.Slf4j;

/**
 * A pool of pre-warmed {@link ContainerEntry} instances.
 *
 * <p>All containers are started in parallel during {@link #initialize()}, paying the ~45s Docker
 * Compose startup cost only once. Scenarios borrow a container via {@link #acquire()} and return
 * it via {@link #release(ContainerEntry)} after teardown, allowing the next scenario to reuse it
 * immediately without any cold-start overhead.
 *
 * <p>Pool size is controlled by the system property {@code flagd.e2e.pool.size} (default: 2).
 *
 * <p>Multiple test classes may share the same JVM fork (Surefire {@code reuseForks=true}). Each
 * class calls {@link #initialize()} and {@link #shutdown()} once. A reference counter ensures
 * that containers are only started on the first {@code initialize()} call and only stopped when
 * the last {@code shutdown()} call is made, preventing one class from destroying containers that
 * are still in use by another class running concurrently in the same JVM.
 */
@Slf4j
public class ContainerPool {

    private static final int POOL_SIZE = Integer.getInteger("flagd.e2e.pool.size", 2);

    private static final BlockingQueue<ContainerEntry> pool = new LinkedBlockingQueue<>();
    private static final List<ContainerEntry> all = new ArrayList<>();
    private static final java.util.concurrent.atomic.AtomicInteger refCount =
            new java.util.concurrent.atomic.AtomicInteger(0);

    public static void initialize() throws Exception {
        if (refCount.getAndIncrement() > 0) {
            log.info("Container pool already initialized (refCount={}), reusing existing pool.", refCount.get());
            return;
        }
        log.info("Starting container pool of size {}...", POOL_SIZE);
        ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE);
        try {
            List<Future<ContainerEntry>> futures = new ArrayList<>();
            for (int i = 0; i < POOL_SIZE; i++) {
                futures.add(executor.submit(ContainerEntry::start));
            }
            for (Future<ContainerEntry> future : futures) {
                ContainerEntry entry = future.get();
                pool.add(entry);
                all.add(entry);
            }
        } catch (Exception e) {
            // Stop any containers that started successfully before the failure
            all.forEach(entry -> {
                try {
                    entry.stop();
                } catch (IOException suppressed) {
                    e.addSuppressed(suppressed);
                }
            });
            pool.clear();
            all.clear();
            refCount.decrementAndGet();
            throw e;
        } finally {
            executor.shutdown();
        }
        log.info("Container pool ready ({} containers).", POOL_SIZE);
    }

    public static void shutdown() {
        int remaining = refCount.decrementAndGet();
        if (remaining > 0) {
            log.info("Container pool still in use by {} class(es), deferring shutdown.", remaining);
            return;
        }
        log.info("Last shutdown call — stopping all containers.");
        all.forEach(entry -> {
            try {
                entry.stop();
            } catch (IOException e) {
                log.warn("Error stopping container entry", e);
            }
        });
        pool.clear();
        all.clear();
    }

    /**
     * Borrow a container from the pool, blocking until one becomes available.
     * The caller MUST call {@link #release(ContainerEntry)} when done.
     */
    public static ContainerEntry acquire() throws InterruptedException {
        return pool.take();
    }

    /** Return a container to the pool so the next scenario can use it. */
    public static void release(ContainerEntry entry) {
        pool.add(entry);
    }
}
