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
import org.testcontainers.DockerClientFactory;

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
            if (initialized.get()) {
                return;
            }
            log.info("Starting container pool of size {}...", POOL_SIZE);
            ExecutorService executor = null;
            List<Future<ContainerEntry>> futures = new ArrayList<>();
            try {
                // Resolve the Testcontainers Docker client once, single-threaded, before starting
                // containers in parallel. Its DockerClientProviderStrategy is loaded via a non
                // thread-safe ServiceLoader; concurrent first-time resolution throws
                // ConcurrentModificationException, leaving containers unstarted.
                DockerClientFactory.instance().client();
                executor = Executors.newFixedThreadPool(POOL_SIZE);
                for (int i = 0; i < POOL_SIZE; i++) {
                    futures.add(executor.submit(ContainerEntry::start));
                }
                for (Future<ContainerEntry> future : futures) {
                    ContainerEntry entry = future.get();
                    pool.add(entry);
                    all.add(entry);
                }
                // Publish only after every container is up, so a failed startup leaves
                // initialized=false and the next acquire() retries instead of blocking on an
                // empty pool forever.
                initialized.set(true);
            } catch (Exception e) {
                stopInFlight(futures, e);
                pool.clear();
                all.clear();
                throw e;
            } finally {
                if (executor != null) {
                    // shutdownNow interrupts container starts still running after a failure.
                    executor.shutdownNow();
                }
            }
            log.info("Container pool ready ({} containers).", POOL_SIZE);
        }
    }

    /**
     * Stop every container produced during a failed startup — both those already recorded in
     * {@link #all} and any that an in-flight future completed after the failure — so a leaked
     * container can't race the next initialization attempt. Cancels futures still running.
     */
    private static void stopInFlight(List<Future<ContainerEntry>> futures, Exception failure) {
        all.forEach(entry -> stopQuietly(entry, failure));
        for (Future<ContainerEntry> future : futures) {
            if (future.cancel(true)) {
                continue; // wasn't started yet — nothing to stop
            }
            try {
                ContainerEntry entry = future.get();
                if (!all.contains(entry)) {
                    stopQuietly(entry, failure);
                }
            } catch (Exception ignored) {
                // future failed or was interrupted; no container to stop
            }
        }
    }

    private static void stopQuietly(ContainerEntry entry, Exception failure) {
        try {
            entry.stop();
        } catch (IOException suppressed) {
            failure.addSuppressed(suppressed);
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
