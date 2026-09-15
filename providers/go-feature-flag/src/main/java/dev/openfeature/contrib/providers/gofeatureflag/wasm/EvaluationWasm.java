package dev.openfeature.contrib.providers.gofeatureflag.wasm;

import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.runtime.WasmException;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.ChicoryException;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.exception.WasmFileNotFound;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.WasmInput;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.Reason;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.val;

/**
 * EvaluationWasm is a class that represents the evaluation of a feature flag
 * it calls an external WASM module to evaluate the feature flag.
 */
public final class EvaluationWasm implements AutoCloseable {
    private final WasiPreview1 wasi;
    private final Instance instance;
    private final ExportFunction evaluate;
    private final ExportFunction malloc;
    private final ExportFunction free;

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
                        .inheritSystem()
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
    }

    /**
     * preWarmWasm is a function that is called to pre-warm the WASM module
     * It calls the malloc function to allocate memory for the WASM module
     * and then calls the free function to free the memory.
     */
    public void preWarmWasm() {
        val message = "".getBytes(StandardCharsets.UTF_8);
        Memory memory = this.instance.memory();
        int len = message.length;
        int ptr = (int) malloc.apply(len)[0];
        memory.write(ptr, message);
        this.free.apply(ptr, len);
    }

    /**
     * Evaluate is a function that evaluates the feature flag using the WASM module.
     *
     * @param wasmInput - the object used to evaluate the feature flag
     * @return the result of the evaluation
     */
    public GoFeatureFlagResponse evaluate(WasmInput wasmInput) {
        int len = 0;
        int ptr = 0;
        try {
            // convert the WasmInput object to JSON string
            val message = Const.SERIALIZE_WASM_MAPPER.writeValueAsBytes(wasmInput);
            // Store the json string in the memory
            Memory memory = this.instance.memory();
            len = message.length;
            ptr = (int) malloc.apply(len)[0];
            memory.write(ptr, message);

            // Call the wasm evaluate function
            val resultPointer = this.evaluate.apply(ptr, len);

            // Read the output
            int valuePosition = (int) ((resultPointer[0] >>> 32) & 0xFFFFFFFFL);
            int valueSize = (int) (resultPointer[0] & 0xFFFFFFFFL);
            val output = memory.readString(valuePosition, valueSize);

            // Convert the output to a WasmOutput object
            return Const.DESERIALIZE_OBJECT_MAPPER.readValue(output, GoFeatureFlagResponse.class);

        } catch (ChicoryException | WasmException e) {
            this.poisoned = true;
            return errorResponse(e);
        } catch (Exception e) {
            return errorResponse(e);
        } finally {
            if (len > 0) {
                this.free.apply(ptr, len);
            }
        }
    }

    private GoFeatureFlagResponse errorResponse(final Exception e) {
        val response = new GoFeatureFlagResponse();
        response.setErrorCode(ErrorCode.GENERAL.name());
        response.setReason(Reason.ERROR.name());
        response.setErrorDetails(e.getMessage());
        return response;
    }
}
