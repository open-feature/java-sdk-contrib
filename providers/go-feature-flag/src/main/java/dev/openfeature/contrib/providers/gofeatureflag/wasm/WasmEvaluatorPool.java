package dev.openfeature.contrib.providers.gofeatureflag.wasm;

import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.exception.WasmFileNotFound;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.WasmInput;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.Reason;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;

/**
 * WasmEvaluatorPool manages a fixed pool of EvaluationWasm instances.
 * Each instance owns independent WASM linear memory, allowing concurrent
 * evaluate() calls without interleaving memory operations.
 */
@Slf4j
public final class WasmEvaluatorPool {
    private final BlockingQueue<EvaluationWasm> pool;

    /**
     * Creates a pool of {@code size} independent EvaluationWasm instances.
     * All instances are allocated eagerly so that first-call latency is
     * absorbed at provider initialisation time.
     *
     * @param size number of WASM instances; must be >= 1
     * @throws WasmFileNotFound if the embedded WASM module cannot be loaded
     */
    public WasmEvaluatorPool(int size) throws WasmFileNotFound {
        this.pool = new ArrayBlockingQueue<>(size);
        for (int i = 0; i < size; i++) {
            if (!pool.offer(new EvaluationWasm())) {
                log.warn(
                        "Failed to add WASM instance {} to pool during initialisation"
                                + " — pool capacity may be exceeded",
                        i);
            }
        }
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
        EvaluationWasm instance;
        try {
            instance = pool.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            GoFeatureFlagResponse err = new GoFeatureFlagResponse();
            err.setErrorCode(ErrorCode.GENERAL.name());
            err.setReason(Reason.ERROR.name());
            err.setErrorDetails("WASM evaluator pool interrupted while waiting for an available instance");
            return err;
        }
        try {
            return instance.evaluate(wasmInput);
        } finally {
            if (!pool.offer(instance)) {
                log.warn("Failed to return WASM instance to pool — pool may be exhausted");
            }
        }
    }

    /**
     * Pre-warms all instances in the pool to avoid cold-start latency on
     * the first evaluation after provider initialisation.
     */
    public void preWarmWasm() {
        pool.forEach(EvaluationWasm::preWarmWasm);
    }
}
