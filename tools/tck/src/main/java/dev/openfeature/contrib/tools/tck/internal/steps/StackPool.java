package dev.openfeature.contrib.tools.tck.internal.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Backends warmed once and leased to one scenario at a time.
 *
 * <p>Off by default: with no size set, every {@link TckObjectFactory} starts one backend and keeps
 * it for as long as its thread lives. The size caps how many stand at once, which a thread count
 * cannot do: {@code cucumber.execution.parallel.config.fixed.parallelism} is a target, not a limit.
 * Surplus threads queue on {@link #acquire()}.
 */
final class StackPool {

    /** How many backends to warm; absent or zero keeps one backend per thread. */
    static final String SIZE_PROPERTY = "openfeature.tck.pool.size";

    /** Every lease is returned by {@code stop()}, so waiting this long means one was lost. */
    private static final long ACQUIRE_TIMEOUT_MINUTES = 10;

    private static final Logger log = LoggerFactory.getLogger(StackPool.class);

    private static final BlockingQueue<TckLifecycle> idle = new LinkedBlockingQueue<>();

    private static final ThreadLocal<Boolean> leaseBroken = ThreadLocal.withInitial(() -> false);

    private static volatile int size;

    /** Backends still fit to lease; drops lower it, so waiting scenarios can fail at zero. */
    private static final AtomicInteger live = new AtomicInteger();

    private StackPool() {
        // warmed and drained by TckLifecycle, leased by TckObjectFactory
    }

    /** Whether scenarios lease backends from this pool. */
    static boolean enabled() {
        return size > 0;
    }

    /**
     * Starts the requested number of backends, concurrently, or does nothing when none was asked
     * for.
     *
     * @param start supplies one started backend, as the per-thread path does
     * @throws IllegalStateException if any backend fails to start
     */
    static void warm(Supplier<TckLifecycle> start) {
        int requested = requestedSize();
        if (requested <= 0) {
            size = 0;
            return;
        }

        log.info("Warming {} backends ({}={})", requested, SIZE_PROPERTY, requested);
        ExecutorService starters = Executors.newFixedThreadPool(requested);
        List<Future<TckLifecycle>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requested; i++) {
                futures.add(starters.submit(start::get));
            }
            List<TckLifecycle> warmed = new ArrayList<>();
            RuntimeException failure = null;
            for (Future<TckLifecycle> future : futures) {
                try {
                    warmed.add(future.get());
                } catch (ExecutionException e) {
                    RuntimeException cause = e.getCause() instanceof RuntimeException
                            ? (RuntimeException) e.getCause()
                            : new IllegalStateException(e.getCause());
                    failure = failure == null ? cause : failure;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while warming the backend pool", e);
                }
            }
            if (failure != null) {
                // failing beats letting scenarios queue for backends that will never arrive
                throw new IllegalStateException(
                        "only " + warmed.size() + " of " + requested + " backends started", failure);
            }
            idle.addAll(warmed);
            size = requested;
            live.set(requested);
        } finally {
            starters.shutdownNow();
        }
    }

    /** Takes a backend for one scenario, waiting while every one of them is in use. */
    static TckLifecycle acquire() {
        leaseBroken.set(false);
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(ACQUIRE_TIMEOUT_MINUTES);
        try {
            while (System.nanoTime() < deadline) {
                if (live.get() <= 0) {
                    throw new IllegalStateException("every backend was dropped after its preparation failed ("
                            + SIZE_PROPERTY + "=" + size + "); there is nothing left to lease");
                }
                // in steps, so a scenario does not wait out the whole deadline for a pool that emptied
                TckLifecycle leased = idle.poll(1, TimeUnit.SECONDS);
                if (leased != null) {
                    return leased;
                }
            }
            throw new IllegalStateException("no backend became free within " + ACQUIRE_TIMEOUT_MINUTES
                    + " minutes, with " + live.get() + " of " + size + " in the pool (" + SIZE_PROPERTY
                    + "); a lease was not returned");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for a backend", e);
        }
    }

    /** Returns a leased backend, dropping it if the scenario reported it broken. */
    static void release(TckLifecycle lifecycle) {
        if (Boolean.TRUE.equals(leaseBroken.get())) {
            leaseBroken.set(false);
            log.warn(
                    "Dropping a backend whose preparation failed; {} of {} left in the pool",
                    live.decrementAndGet(),
                    size);
            return;
        }
        idle.add(lifecycle);
    }

    /** Marks the backend this thread holds as unfit to pass on; called where the breakage shows. */
    static void markLeaseBroken() {
        if (enabled()) {
            leaseBroken.set(true);
        }
    }

    /** Forgets every lease; the backends themselves are stopped by {@code endSuite()}. */
    static void drain() {
        idle.clear();
        size = 0;
        live.set(0);
    }

    private static int requestedSize() {
        String raw = System.getProperty(SIZE_PROPERTY);
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < 0) {
                throw new IllegalArgumentException(
                        SIZE_PROPERTY + " is " + raw + "; a pool cannot hold fewer than no backends.");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    SIZE_PROPERTY + " is '" + raw + "', which is not a number of backends.", e);
        }
    }
}
