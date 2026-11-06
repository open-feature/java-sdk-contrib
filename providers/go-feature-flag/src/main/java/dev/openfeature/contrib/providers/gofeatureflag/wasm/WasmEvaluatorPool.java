package dev.openfeature.contrib.providers.gofeatureflag.wasm;

import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.exception.WasmFileNotFound;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.WasmInput;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.Reason;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * WasmEvaluatorPool manages a fixed pool of EvaluationWasm instances.
 * Each instance owns independent WASM linear memory, allowing concurrent
 * evaluate() calls without interleaving memory operations.
 */
@Slf4j
public final class WasmEvaluatorPool implements AutoCloseable {
    /** how long an evaluation waits for an instance before checking again whether the pool was closed. */
    private static final long CLOSED_CHECK_INTERVAL_MS = 100L;

    private final BlockingQueue<EvaluationWasm> pool;
    private final Supplier<EvaluationWasm> instanceFactory;
    /** instances discarded after a fault whose replacement could not be built yet. */
    private final AtomicInteger missing = new AtomicInteger();

    @Getter
    private volatile boolean closed;

    /**
     * Creates a pool of {@code size} independent EvaluationWasm instances.
     * All instances are allocated and warmed up eagerly so that first-call latency is
     * absorbed in provider initialisation time: each slot costs roughly 20 ms of warm-up.
     *
     * @param size number of WASM instances; must be >= 1
     * @throws WasmFileNotFound if the embedded WASM module cannot be loaded
     */
    public WasmEvaluatorPool(int size) throws WasmFileNotFound {
        this(size, EvaluationWasm::new);
    }

    WasmEvaluatorPool(int size, Supplier<EvaluationWasm> instanceFactory) throws WasmFileNotFound {
        this.instanceFactory = instanceFactory;
        this.pool = new ArrayBlockingQueue<>(size);
        for (int i = 0; i < size; i++) {
            pool.add(newInstance());
        }
    }

    private EvaluationWasm newInstance() {
        EvaluationWasm instance = instanceFactory.get();
        instance.preWarmWasm();
        return instance;
    }

    /**
     * Evaluates a feature flag by borrowing one WASM instance from the pool,
     * delegating to it, and returning it when done.
     * Blocks if all instances are busy until one becomes available, or until the pool is closed.
     *
     * @param wasmInput evaluation input
     * @return evaluation result
     */
    public GoFeatureFlagResponse evaluate(WasmInput wasmInput) {
        EvaluationWasm instance = null;
        try {
            // close() empties the queue and nothing is offered back afterwards, so a plain take()
            // would wait forever: waiting in slices lets a waiter notice the pool has been closed.
            while (instance == null) {
                if (closed) {
                    return errorResponse("WASM evaluator pool is closed");
                }
                instance = pool.poll(CLOSED_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (instance == null && claimMissingInstance()) {
                    instance = rebuild();
                    if (instance == null) {
                        missing.incrementAndGet();
                        return errorResponse("no WASM instance is available and none could be rebuilt");
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorResponse("WASM evaluator pool interrupted while waiting for an available instance");
        }
        try {
            return instance.evaluate(wasmInput);
        } finally {
            returnToPool(instance);
        }
    }

    /**
     * returnToPool gives the instance back, replacing it first if the guest faulted while it was
     * serving. A trapped instance is permanently poisoned and must never be reused, so it is dropped
     * and a fresh one takes its place; if the replacement cannot be built, an evaluation that finds
     * the pool empty builds it later.
     */
    private void returnToPool(final EvaluationWasm instance) {
        EvaluationWasm toReturn = instance;
        if (instance.isPoisoned()) {
            log.warn("discarding a WASM instance whose guest faulted, and rebuilding it");
            closeQuietly(instance);
            toReturn = rebuild();
            if (toReturn == null) {
                missing.incrementAndGet();
                return;
            }
        }
        if (closed) {
            closeQuietly(toReturn);
            return;
        }
        if (!pool.offer(toReturn)) {
            log.error("Failed to return WASM instance to pool - instance leaked, pool capacity may be compromised");
            closeQuietly(toReturn);
        }
    }

    /**
     * rebuild builds a replacement instance. The interrupt flag is cleared while it runs, because
     * the module checks it and would fail the build on a thread interrupted during the evaluation
     * that poisoned the previous instance.
     *
     * @return the new instance, or null if it could not be built
     */
    private EvaluationWasm rebuild() {
        boolean interrupted = Thread.interrupted();
        try {
            return newInstance();
        } catch (Exception | Error e) {
            log.error("failed to rebuild a WASM instance", e);
            return null;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * claimMissingInstance takes responsibility for rebuilding one of the missing instances.
     *
     * @return true if an instance is missing and this caller must rebuild it
     */
    private boolean claimMissingInstance() {
        int current = missing.get();
        while (current > 0) {
            if (missing.compareAndSet(current, current - 1)) {
                return true;
            }
            current = missing.get();
        }
        return false;
    }

    /**
     * close releases every instance the pool owns. An instance still serving an evaluation is not in
     * the queue, so returnToPool closes it as soon as it comes back rather than handing it to a pool
     * nobody will read again.
     */
    @Override
    public void close() {
        closed = true;
        EvaluationWasm instance = pool.poll();
        while (instance != null) {
            closeQuietly(instance);
            instance = pool.poll();
        }
    }

    /**
     * Releasing an instance is best effort: a descriptor that refuses to close must not abort a
     * provider shutdown, nor the rebuild of an instance whose guest faulted.
     */
    private void closeQuietly(final EvaluationWasm instance) {
        try {
            instance.close();
        } catch (Exception e) {
            log.warn("failed to release a WASM instance", e);
        }
    }

    private GoFeatureFlagResponse errorResponse(final String details) {
        GoFeatureFlagResponse err = new GoFeatureFlagResponse();
        err.setErrorCode(ErrorCode.GENERAL.name());
        err.setReason(Reason.ERROR.name());
        err.setErrorDetails(details);
        return err;
    }
}
