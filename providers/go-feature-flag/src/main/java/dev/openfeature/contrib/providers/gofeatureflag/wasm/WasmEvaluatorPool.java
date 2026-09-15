package dev.openfeature.contrib.providers.gofeatureflag.wasm;

import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.exception.WasmFileNotFound;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.WasmInput;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.Reason;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * WasmEvaluatorPool manages a fixed pool of EvaluationWasm instances.
 * Each instance owns independent WASM linear memory, allowing concurrent
 * evaluate() calls without interleaving memory operations.
 */
@Slf4j
public final class WasmEvaluatorPool implements AutoCloseable {
    private final BlockingQueue<EvaluationWasm> pool;
    private final Supplier<EvaluationWasm> instanceFactory;

    private volatile boolean closed;

    /**
     * Creates a pool of {@code size} independent EvaluationWasm instances.
     * All instances are allocated eagerly so that first-call latency is
     * absorbed in provider initialisation time.
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
     * Blocks if all instances are busy until one becomes available.
     *
     * @param wasmInput evaluation input
     * @return evaluation result
     */
    public GoFeatureFlagResponse evaluate(WasmInput wasmInput) {
        if (closed) {
            return errorResponse("WASM evaluator pool is closed");
        }
        EvaluationWasm instance;
        try {
            instance = pool.take();
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
     * and a fresh one takes its place; if the replacement cannot be built the pool simply shrinks,
     * which is preferable to handing out a corrupted instance.
     */
    private void returnToPool(final EvaluationWasm instance) {
        EvaluationWasm toReturn = instance;
        if (instance.isPoisoned()) {
            log.warn("discarding a WASM instance whose guest faulted, and rebuilding it");
            closeQuietly(instance);
            try {
                toReturn = newInstance();
            } catch (Exception e) {
                log.error("failed to rebuild a WASM instance, the evaluation pool has shrunk", e);
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
