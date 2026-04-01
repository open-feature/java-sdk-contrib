package dev.openfeature.contrib.providers.flagd.e2e;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * A pool of pre-warmed {@link ContainerEntry} instances.
 *
 * <p>All containers are started in parallel on the first {@link #acquire()} call, paying the
 * Docker Compose startup cost only once per JVM. Scenarios borrow a container via
 * {@link #acquire()} and return it via {@link #release(ContainerEntry)} after teardown.
 *
 * <p>Cleanup is handled automatically via a JVM shutdown hook — no explicit lifecycle calls are
 * needed from test classes. This means multiple test classes (e.g. several {@code @Suite} runners
 * or {@code @TestFactory} methods) share the same pool across the entire JVM lifetime without
 * redundant container startups.
 *
 * <p>Pool size is controlled by the system property {@code flagd.e2e.pool.size}
 * (default: min(availableProcessors, 4)).
 */
@Slf4j
public class ContainerPool {

    private static final int POOL_SIZE = Integer.getInteger(
            "flagd.e2e.pool.size", Math.min(Runtime.getRuntime().availableProcessors(), 4));

    private static final BlockingQueue<ContainerEntry> pool = new LinkedBlockingQueue<>();
    private static final List<ContainerEntry> all = new ArrayList<>();
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * JVM-wide semaphore that serializes disruptive container operations (stop/restart) across all
     * parallel Cucumber engines. Only one scenario at a time may bring a container down, preventing
     * cascading initialization timeouts in sibling scenarios that are waiting for a container slot.
     */
    private static final Semaphore restartSlot = new Semaphore(1);

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ContainerPool::stopAll, "container-pool-shutdown"));
    }

    /**
     * Borrow a container from the pool, blocking until one becomes available.
     * Initializes the pool on the first call. The caller MUST call
     * {@link #release(ContainerEntry)} when done.
     */
    public static ContainerEntry acquire() throws Exception {
        ensureInitialized();
        return pool.take();
    }

    /** Return a container to the pool so the next scenario can use it. */
    public static void release(ContainerEntry entry) {
        pool.add(entry);
    }

    /**
     * Acquires the JVM-wide restart slot before stopping or restarting a container.
     * Must be paired with {@link #releaseRestartSlot()} in the scenario {@code @After} hook.
     */
    public static void acquireRestartSlot() throws InterruptedException {
        log.debug("Acquiring restart slot...");
        restartSlot.acquire();
        log.debug("Restart slot acquired.");
    }

    /** Releases the JVM-wide restart slot acquired by {@link #acquireRestartSlot()}. */
    public static void releaseRestartSlot() {
        restartSlot.release();
        log.debug("Restart slot released.");
    }

    private static void ensureInitialized() throws Exception {
        if (initialized.get()) {
            return;
        }
        synchronized (ContainerPool.class) {
            if (!initialized.compareAndSet(false, true)) {
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
                all.forEach(entry -> {
                    try {
                        entry.stop();
                    } catch (IOException suppressed) {
                        e.addSuppressed(suppressed);
                    }
                });
                pool.clear();
                all.clear();
                initialized.set(false);
                throw e;
            } finally {
                executor.shutdown();
            }
            log.info("Container pool ready ({} containers).", POOL_SIZE);
        }
    }

    private static void stopAll() {
        if (all.isEmpty()) return;
        log.info("Shutdown hook — stopping all containers.");
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
}
