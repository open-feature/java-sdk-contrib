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
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Backends warmed once and leased to one scenario at a time.
 *
 * <p>Off by default: with no size set, every {@link TckObjectFactory} starts one backend and keeps
 * it for as long as its thread lives, which is the behaviour every adoption has had so far. Set
 * {@value #SIZE_PROPERTY} and the suite instead starts that many backends up front, hands one to
 * each scenario, and takes it back afterwards.
 *
 * <p><strong>The size is the cap on how many backends stand at once.</strong> That is the reason to
 * prefer it over a thread count: {@code cucumber.execution.parallel.config.fixed.parallelism} is a
 * target rather than a limit, and a suite whose steps block has been observed growing to eighteen
 * threads -- each of which would otherwise start a backend of its own. Here the surplus threads
 * queue on {@link #acquire()} instead, and the number of containers is whatever was declared.
 *
 * <p>A scenario receives a backend that the next {@code prepareScenario} has cleaned rather than one
 * nobody has touched, so the pool does not weaken the contract that every scenario starts from the
 * baseline -- it widens who it protects, because a backend now moves between scenarios of different
 * features and suites rather than staying on one thread.
 */
final class StackPool {

    /** How many backends to warm; absent or zero keeps one backend per thread. */
    static final String SIZE_PROPERTY = "openfeature.tck.pool.size";

    /**
     * How long {@link #acquire()} waits before calling a lease leak a leak.
     *
     * <p>Every lease is returned by {@link TckObjectFactory#stop()}, which Cucumber calls after the
     * scenario's own hooks, so a scenario waiting this long means a lease was lost rather than that
     * the suite is busy. Blocking forever would hang the build with no explanation.
     */
    private static final long ACQUIRE_TIMEOUT_MINUTES = 10;

    private static final Logger log = LoggerFactory.getLogger(StackPool.class);

    private static final BlockingQueue<TckLifecycle> idle = new LinkedBlockingQueue<>();

    /** The leases handed out but not yet returned, so a broken one can be named in a message. */
    private static final ThreadLocal<Boolean> leaseBroken = ThreadLocal.withInitial(() -> false);

    private static volatile int size;

    private StackPool() {
        // warmed and drained by TckLifecycle, leased by TckObjectFactory
    }

    /**
     * Whether scenarios lease backends from this pool.
     *
     * @return true once a suite has warmed a pool of at least one backend
     */
    static boolean enabled() {
        return size > 0;
    }

    /**
     * Starts the requested number of backends, concurrently, or does nothing when none was asked
     * for.
     *
     * <p>Concurrently because the startups are the cost the pool exists to pay once: starting four
     * backends one after another would spend four times a Compose startup before the first scenario
     * runs.
     *
     * @param start supplies one started backend, as the per-thread path does
     * @throws IllegalStateException if any backend fails to start, after stopping the ones that did
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
                // the ones that came up are registered for endSuite(), which stops them; the suite
                // fails here rather than letting scenarios queue for backends that will never arrive
                throw new IllegalStateException(
                        "only " + warmed.size() + " of " + requested + " backends started", failure);
            }
            idle.addAll(warmed);
            size = requested;
        } finally {
            starters.shutdownNow();
        }
    }

    /**
     * Takes a backend for one scenario, waiting while every one of them is in use.
     *
     * @return the leased backend
     */
    static TckLifecycle acquire() {
        leaseBroken.set(false);
        try {
            TckLifecycle leased = idle.poll(ACQUIRE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (leased == null) {
                throw new IllegalStateException("no backend became free within " + ACQUIRE_TIMEOUT_MINUTES
                        + " minutes, with " + size + " in the pool (" + SIZE_PROPERTY
                        + "); a lease was not returned");
            }
            return leased;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for a backend", e);
        }
    }

    /**
     * Returns a leased backend, unless the scenario reported it broken.
     *
     * <p>A backend whose own preparation failed is dropped rather than handed to the next scenario:
     * returning it would spread one failure across every scenario that drew it, and the pool would
     * quietly become a set of broken backends that fail everything. Shrinking is loud and the
     * remaining backends still work; the suite fails when the last one goes.
     *
     * @param lifecycle the backend to return
     */
    static void release(TckLifecycle lifecycle) {
        if (Boolean.TRUE.equals(leaseBroken.get())) {
            leaseBroken.set(false);
            log.warn("Dropping a backend whose preparation failed; {} of {} left in the pool", idle.size(), size);
            return;
        }
        idle.add(lifecycle);
    }

    /**
     * Marks the backend this thread holds as unfit to pass on.
     *
     * <p>Called where the breakage is visible -- the step that prepares the backend -- because
     * {@link TckObjectFactory#stop()} only knows that a scenario ended, not why.
     */
    static void markLeaseBroken() {
        if (enabled()) {
            leaseBroken.set(true);
        }
    }

    /** Forgets every lease; the backends themselves are stopped by {@code endSuite()}. */
    static void drain() {
        idle.clear();
        size = 0;
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
