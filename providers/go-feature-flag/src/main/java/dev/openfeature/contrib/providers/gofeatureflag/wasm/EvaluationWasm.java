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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import lombok.SneakyThrows;
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

    @SneakyThrows
    public EvaluationWasm() {
        // We will create two output streams to capture stdout and stderr
        val wasi = WasiPreview1.builder().withOptions(WasiOptions.builder().inheritSystem().build()).build();
        val hostFunctions = wasi.toHostFunctions();
        val store = new Store().addFunction(hostFunctions);
        store.addFunction(getProcExitFunc());
        this.instance = store.instantiate("evaluation", Parser.parse(getWasmFile()));
        this.evaluate = this.instance.export("evaluate");
        this.malloc = this.instance.export("malloc");
        this.free = this.instance.export("free");
    }

    /**
     * getWasmFile is a function that returns the path to the WASM file
     * It looks for the file in the classpath under the directory "wasm"
     *
     * @return the path to the WASM file
     * @throws WasmFileNotFound - if the file is not found
     */
    private File getWasmFile() throws WasmFileNotFound {
        try {
            ClassLoader classLoader = EvaluationWasm.class.getClassLoader();
            URL directoryURL = classLoader.getResource("wasm");
            if (directoryURL == null) {
                throw new RuntimeException("Directory not found");
            }
            Path dirPath = Paths.get(directoryURL.toURI());
            try (val files = Files.list(dirPath)) {
                return files
                        .filter(path -> path.getFileName().toString().startsWith("gofeatureflag-evaluation")
                                && (path.toString().endsWith(".wasi") || path.toString().endsWith(".wasm")))
                        .findFirst()
                        .map(Path::toFile)
                        .orElseThrow(
                                () -> new RuntimeException("No file starting with 'gofeatureflag-evaluation' found"));
            }
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
        return new HostFunction("wasi_snapshot_preview1", "proc_exit",
                Collections.singletonList(ValueType.I32), Collections.emptyList(), (instance, args) -> {
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
        val message = "".getBytes();
        Memory memory = this.instance.memory();
        int len = message.length;
        int ptr = (int) malloc.apply(len)[0];
        memory.write(ptr, message);
        this.free.apply(ptr, len);
    }


    /**
     * Evaluate is a function that evaluates the feature flag using the WASM module
     *
     * @param wasmInput - the object used to evaluate the feature flag
     * @return the result of the evaluation
     */
    public GoFeatureFlagResponse evaluate(WasmInput wasmInput) {
        try {
            // convert the WasmInput object to JSON string
            val message = Const.SERIALIZE_WASM_MAPPER.writeValueAsBytes(wasmInput);
            // Store the json string in the memory
            Memory memory = this.instance.memory();
            int len = message.length;
            int ptr = (int) malloc.apply(len)[0];
            memory.write(ptr, message);

            // Call the wasm evaluate function
            val resultPointer = this.evaluate.apply(ptr, len);

            // Read the output
            int valuePosition = (int) ((resultPointer[0] >>> 32) & 0xFFFFFFFFL);
            int valueSize = (int) (resultPointer[0] & 0xFFFFFFFFL);
            val output = memory.readString(valuePosition, valueSize);

            // Free the memory
            this.free.apply(ptr, len);

            // Convert the output to a WasmOutput object
            return Const.DESERIALIZE_OBJECT_MAPPER.readValue(output, GoFeatureFlagResponse.class);

        } catch (Exception e) {
            System.out.println(e.getMessage());
            val response = new GoFeatureFlagResponse();
            response.setErrorCode(ErrorCode.GENERAL.name());
            response.setReason(Reason.ERROR.name());
            response.setErrorDetails(e.getMessage());
            return response;
        }
    }
}
