package dev.openfeature.contrib.providers.gofeatureflag.wasm;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.runtime.Store;
import com.dylibso.chicory.wasi.WasiExitException;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.ValueType;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.exception.WasmFileNotFound;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.WasmInput;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.Reason;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import lombok.val;

/**
 * EvaluationWasm is a class that represents the evaluation of a feature flag
 * it calls an external WASM module to evaluate the feature flag.
 */
public class EvaluationWasm {
    private final Instance instance;
    private final ExportFunction evaluate;
    private final ExportFunction malloc;
    private final ExportFunction free;

    /**
     * Constructor of the EvaluationWasm.
     * It initializes the WASM module and the host functions.
     *
     * @throws WasmFileNotFound - if the WASM file is not found
     */
    public EvaluationWasm() throws WasmFileNotFound {
        // We will create two output streams to capture stdout and stderr
        val wasi = WasiPreview1.builder()
                .withOptions(WasiOptions.builder().inheritSystem().build())
                .build();
        val hostFunctions = wasi.toHostFunctions();
        val store = new Store().addFunction(hostFunctions);
        store.addFunction(getProcExitFunc());
        this.instance = store.instantiate("evaluation", Parser.parse(getWasmFile()));
        this.evaluate = this.instance.export("evaluate");
        this.malloc = this.instance.export("malloc");
        this.free = this.instance.export("free");
    }

    /**
     * getWasmFile is a function that returns the path to the WASM file.
     * It looks for the file in the classpath under the directory "wasm".
     * This method handles both file system resources and JAR-packaged resources.
     *
     * @return the path to the WASM file
     * @throws WasmFileNotFound - if the file is not found
     */
    private InputStream getWasmFile() throws WasmFileNotFound {
        try {
            final String wasmResourcePath = "wasm/gofeatureflag-evaluation.wasi";
            InputStream inputStream = EvaluationWasm.class.getClassLoader().getResourceAsStream(wasmResourcePath);
            if (inputStream == null) {
                throw new WasmFileNotFound("WASM resource not found in classpath: " + wasmResourcePath);
            }
            return inputStream;
        } catch (WasmFileNotFound e) {
            throw e;
        } catch (Exception e) {
            throw new WasmFileNotFound(e);
        }
    }

    /**
     * getProcExitFunc is a function that is called when the WASM module calls
     * proc_exit. It throws a WasiExitException with the exit code.
     * By default, the exit code is 0, and it raises an Exception.
     *
     * @return a HostFunction that is called when the WASM module calls proc_exit
     */
    private HostFunction getProcExitFunc() {
        return new HostFunction(
                "wasi_snapshot_preview1",
                "proc_exit",
                Collections.singletonList(ValueType.I32),
                Collections.emptyList(),
                (instance, args) -> {
                    if ((int) args[0] != 0) {
                        throw new WasiExitException((int) args[0]);
                    }
                    return null;
                });
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

        } catch (Exception e) {
            val response = new GoFeatureFlagResponse();
            response.setErrorCode(ErrorCode.GENERAL.name());
            response.setReason(Reason.ERROR.name());
            response.setErrorDetails(e.getMessage());
            return response;
        } finally {
            if (len > 0) {
                this.free.apply(ptr, len);
            }
        }
    }
}
