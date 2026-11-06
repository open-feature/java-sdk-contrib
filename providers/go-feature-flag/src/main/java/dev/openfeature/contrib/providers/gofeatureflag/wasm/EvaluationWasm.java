package dev.openfeature.contrib.providers.gofeatureflag.wasm;

import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.exception.WasmFileNotFound;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.WasmInput;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.Reason;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * EvaluationWasm is a class that represents the evaluation of a feature flag
 * it calls an external WASM module to evaluate the feature flag.
 */
@Slf4j
public final class EvaluationWasm implements AutoCloseable {
    private final WasiPreview1 wasi;
    private final Instance instance;
    private final ExportFunction evaluate;
    private final ExportFunction malloc;
    private final ExportFunction free;
    /** the engine writes nothing while evaluating normally, so anything it prints reports a fault. */
    private final WasmGuestOutput stdout = new WasmGuestOutput(line -> log.error("evaluation engine: {}", line));

    private final WasmGuestOutput stderr = new WasmGuestOutput(line -> log.error("evaluation engine: {}", line));

    /**
     * A complete evaluation with a targeting rule, the shape of the canonical ABI vector. The rule
     * matters: parsing its query is the bulk of what the engine initialises lazily on first use.
     */
    private static final byte[] WARM_UP_INPUT = ("{\"flagKey\":\"warm-up\","
                    + "\"flag\":{\"variations\":{\"enabled\":true,\"disabled\":false},"
                    + "\"targeting\":[{\"query\":\"targetingKey eq \\\"warm-up\\\"\",\"variation\":\"enabled\"}],"
                    + "\"defaultRule\":{\"variation\":\"disabled\"}},"
                    + "\"evalContext\":{\"targetingKey\":\"warm-up\"},"
                    + "\"flagContext\":{\"defaultSdkValue\":false}}")
            .getBytes(StandardCharsets.UTF_8);

    /**
     * poisoned is set when the guest faults. A trap does not unwind the module's shadow-stack
     * pointer, so the instance is permanently unusable and must never serve another evaluation.
     */
    @Getter
    private volatile boolean poisoned;

    /**
     * Constructor of the EvaluationWasm.
     * It initializes the WASM module and the host functions.
     *
     * @throws WasmFileNotFound - if the WASM file is not found
     */
    public EvaluationWasm() throws WasmFileNotFound {
        this.wasi = WasiPreview1.builder()
                .withOptions(WasiOptions.builder()
                        .withStdout(this.stdout)
                        .withStderr(this.stderr)
                        .withThrowOnExit0(false)
                        .build())
                .build();
        this.instance = Instance.builder(Module.load())
                .withMemoryFactory(ByteArrayMemory::new)
                .withMachineFactory(Module::create)
                .withImportValues(ImportValues.builder()
                        .addFunction(this.wasi.toHostFunctions())
                        .build())
                .build();
        this.evaluate = this.instance.export("evaluate");
        this.malloc = this.instance.export("malloc");
        this.free = this.instance.export("free");
    }

    /**
     * close releases the descriptors the WASI instance owns. The WASI object serves the guest's
     * imports for as long as the module is alive, so it can only be released once this evaluator is
     * discarded, never at the end of the constructor.
     */
    @Override
    public void close() {
        this.wasi.close();
        this.stdout.close();
        this.stderr.close();
    }

    /**
     * preWarmWasm runs one throwaway evaluation so that the engine's lazy initialisation, and the
     * loading of the classes it was compiled to, happen now rather than on the first real evaluation
     * this instance serves. The answer is discarded; a fault propagates like any other.
     */
    public void preWarmWasm() {
        evaluateRaw(WARM_UP_INPUT);
    }

    /**
     * Evaluate is a function that evaluates the feature flag using the WASM module.
     *
     * @param wasmInput - the object used to evaluate the feature flag
     * @return the result of the evaluation
     */
    public GoFeatureFlagResponse evaluate(WasmInput wasmInput) {
        try {
            val output = evaluateRaw(Const.SERIALIZE_WASM_MAPPER.writeValueAsBytes(wasmInput));
            return Const.DESERIALIZE_OBJECT_MAPPER.readValue(output, GoFeatureFlagResponse.class);
        } catch (Exception | Error e) {
            return errorResponse(e);
        }
    }

    /**
     * evaluateRaw hands the engine a raw input and returns its raw output, across the engine ABI.
     *
     * @param message - the JSON input of the engine
     * @return the JSON output of the engine
     */
    String evaluateRaw(final byte[] message) {
        int ptr = 0;
        try {
            Memory memory = this.instance.memory();
            ptr = (int) malloc.apply(message.length)[0];
            memory.write(ptr, message);

            val resultPointer = this.evaluate.apply(ptr, message.length);

            int valuePosition = (int) ((resultPointer[0] >>> 32) & 0xFFFFFFFFL);
            int valueSize = (int) (resultPointer[0] & 0xFFFFFFFFL);
            return memory.readString(valuePosition, valueSize);
        } catch (RuntimeException | Error e) {
            // anything escaping the guest, an OutOfMemoryError from memory.grow included, aborted it mid-call
            this.poisoned = true;
            throw e;
        } finally {
            freeInput(ptr, message.length);
        }
    }

    /**
     * freeInput releases the input buffer, unless the guest faulted while serving this evaluation.
     *
     * <p>Calling into a trapped instance faults inside malloc at a wrapped address, which masks the
     * original error; the instance is discarded by the pool anyway, so its memory is not worth
     * reclaiming. A fault raised by free itself is contained here for the same reason.</p>
     */
    private void freeInput(final int ptr, final int len) {
        if (len <= 0 || this.poisoned) {
            return;
        }
        try {
            this.free.apply(ptr);
        } catch (RuntimeException | Error e) {
            this.poisoned = true;
            log.error("failed to free the WASM input buffer, the instance will be discarded", e);
        }
    }

    private GoFeatureFlagResponse errorResponse(final Throwable e) {
        val response = new GoFeatureFlagResponse();
        response.setErrorCode(ErrorCode.GENERAL.name());
        response.setReason(Reason.ERROR.name());
        response.setErrorDetails(e.getMessage());
        return response;
    }
}
