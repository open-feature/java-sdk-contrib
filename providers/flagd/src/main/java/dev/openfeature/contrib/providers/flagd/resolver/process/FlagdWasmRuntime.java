package dev.openfeature.contrib.providers.flagd.resolver.process;

import com.dylibso.chicory.compiler.InterpreterFallback;
import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Machine;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.runtime.Store;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ValType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * Runtime environment for the flagd-evaluator WASM module.
 *
 * This class handles:
 * - Loading and compiling the WASM module
 * - Providing host functions required by the WASM module
 * - Creating configured instances of the WASM module
 */
public final class FlagdWasmRuntime {

    private static final WasmModule WASM_MODULE;
    private static final Function<Instance, Machine> MACHINE_FUNCTION;

    static {
        byte[] wasmBytes;
        try {
            wasmBytes = Files.readAllBytes(Path.of("flagd_evaluator.wasm"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load flagd_evaluator.wasm", e);
        }
        WASM_MODULE = Parser.parse(wasmBytes);
        MACHINE_FUNCTION = MachineFactoryCompiler.builder(WASM_MODULE)
                .withInterpreterFallback(InterpreterFallback.WARN)
                .compile();
    }

    private FlagdWasmRuntime() {
        // Utility class - prevent instantiation
    }

    /**
     * Get the WASM module.
     *
     * @return the parsed WASM module
     */
    public static WasmModule getModule() {
        return WASM_MODULE;
    }

    /**
     * Get the machine factory function.
     *
     * @return the compiled machine factory
     */
    public static Function<Instance, Machine> getMachineFunction() {
        return MACHINE_FUNCTION;
    }

    /**
     * Create a Store with all required host functions for the flagd-evaluator WASM module.
     *
     * The WASM module requires exactly 9 host functions:
     *
     * CRITICAL (2):
     * - get_current_time_unix_seconds: Provides Unix timestamp for $flagd.timestamp context enrichment
     * - getRandomValues: Provides cryptographic entropy for ahash (used by boon JSON schema validation)
     *
     * LEGACY (2):
     * - new_0/getTime: Legacy Date-based timestamp (kept for backward compatibility, may be removed)
     *
     * ERROR HANDLING (1):
     * - throw: Allows WASM to throw exceptions to Java
     *
     * NO-OPS (4):
     * - describe: Type description (wasm-bindgen artifact, no-op)
     * - object_drop_ref: Object reference counting (wasm-bindgen artifact, no-op)
     * - externref_table_grow/set_null: External reference table management (wasm-bindgen artifact, no-ops)
     *
     * @return a Store configured with all required host functions
     */
    public static Store createStoreWithHostFunctions() {
        Store store = new Store();
        store.addFunction(
                // CRITICAL: Custom host function for timestamp
                createGetCurrentTimeUnixSeconds(),

                // CRITICAL: Random entropy for ahash in boon
                createGetRandomValues(),

                // LEGACY: Date-based timestamp (may be removed in future)
                createNew0(),
                createGetTime(),

                // ERROR HANDLING
                createWbindgenThrow(),

                // NO-OPS: wasm-bindgen artifacts
                createDescribe(),
                createObjectDropRef(),
                createExternrefTableGrow(),
                createExternrefTableSetNull());
        return store;
    }

    /**
     * Create a configured WASM instance ready for use.
     *
     * @return a WASM instance with all host functions and the AOT compiler
     */
    public static Instance createInstance() {
        Store store = createStoreWithHostFunctions();
        return Instance.builder(WASM_MODULE)
                .withImportValues(store.toImportValues())
                .withMachineFactory(MACHINE_FUNCTION)
                .build();
    }

    // ========================================================================
    // Host Function Implementations
    // ========================================================================

    /**
     * CRITICAL: Provides random bytes for hash table seeding.
     *
     * This is called by getrandom (used by ahash in boon validation).
     * Without proper random bytes, the WASM module will panic.
     */
    private static HostFunction createGetRandomValues() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbg_getRandomValues_1c61fac11405ffdc",
                FunctionType.of(List.of(ValType.I32, ValType.I32), List.of()),
                (Instance instance, long... args) -> {
                    // CRITICAL: This is called by getrandom to get entropy for ahash
                    // ahash uses this for hash table seed generation in boon validation
                    int typedArrayPtr = (int) args[0]; // Unused externref handle
                    int bufferPtr = (int) args[1]; // Pointer to buffer in WASM memory

                    // The WASM code expects a 32-byte buffer at bufferPtr
                    // Fill it with cryptographically secure random bytes
                    byte[] randomBytes = new byte[32];
                    new java.security.SecureRandom().nextBytes(randomBytes);

                    Memory memory = instance.memory();
                    memory.write(bufferPtr, randomBytes);

                    return null;
                });
    }

    /**
     * Creates a new Date object.
     * Used for $flagd.timestamp in evaluation context.
     */
    private static HostFunction createNew0() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbg_new_0_23cedd11d9b40c9d",
                FunctionType.of(List.of(), List.of(ValType.I32)),
                (Instance instance, long... args) -> {
                    // Return a dummy reference
                    return new long[] {0L};
                });
    }

    /**
     * Gets timestamp from Date object.
     * Returns current time in milliseconds as f64.
     * Used for $flagd.timestamp in evaluation context.
     */
    private static HostFunction createGetTime() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbg_getTime_ad1e9878a735af08",
                FunctionType.of(List.of(ValType.I32), List.of(ValType.F64)),
                (Instance instance, long... args) -> {
                    // Return current time in milliseconds
                    return new long[] {Double.doubleToRawLongBits((double) System.currentTimeMillis())};
                });
    }

    /**
     * Throws an exception with a message from WASM memory.
     */
    private static HostFunction createWbindgenThrow() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbg___wbindgen_throw_dd24417ed36fc46e",
                FunctionType.of(List.of(ValType.I32, ValType.I32), List.of()),
                (Instance instance, long... args) -> {
                    // Throw exception - read the error message from memory
                    int ptr = (int) args[0];
                    int len = (int) args[1];
                    Memory memory = instance.memory();
                    String message = memory.readString(ptr, len);
                    throw new RuntimeException("WASM threw: " + message);
                });
    }

    /**
     * Manages externref table growth.
     * No-op in our context as we don't use externrefs.
     */
    private static HostFunction createExternrefTableGrow() {
        return new HostFunction(
                "__wbindgen_externref_xform__",
                "__wbindgen_externref_table_grow",
                FunctionType.of(List.of(ValType.I32), List.of(ValType.I32)),
                (Instance instance, long... args) -> {
                    // Return previous size
                    return new long[] {128L};
                });
    }

    /**
     * Sets externref table entry to null.
     * No-op in our context as we don't use externrefs.
     */
    private static HostFunction createExternrefTableSetNull() {
        return new HostFunction(
                "__wbindgen_externref_xform__",
                "__wbindgen_externref_table_set_null",
                FunctionType.of(List.of(ValType.I32), List.of()),
                (Instance instance, long... args) -> {
                    // No-op: We don't maintain an externref table
                    return null;
                });
    }

    /**
     * Drops an object reference.
     * No-op in our context as we don't track JS objects.
     */
    private static HostFunction createObjectDropRef() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_object_drop_ref",
                FunctionType.of(List.of(ValType.I32), List.of()),
                (Instance instance, long... args) -> {
                    // No-op: We're not tracking JS objects in Java
                    return null;
                });
    }

    /**
     * Describes a type.
     * No-op in our context as type description isn't needed at runtime.
     */
    private static HostFunction createDescribe() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_describe",
                FunctionType.of(List.of(ValType.I32), List.of()),
                (Instance instance, long... args) -> {
                    // No-op: Type description not needed at runtime
                    return null;
                });
    }

    /**
     * CRITICAL: Provides the current Unix timestamp.
     *
     * This is the main host function required for $flagd.timestamp context enrichment.
     * Returns Unix timestamp in seconds since epoch (1970-01-01 00:00:00 UTC).
     */
    private static HostFunction createGetCurrentTimeUnixSeconds() {
        return new HostFunction(
                "host",
                "get_current_time_unix_seconds",
                FunctionType.of(List.of(), List.of(ValType.I64)),
                (Instance instance, long... args) -> {
                    long currentTimeSeconds = System.currentTimeMillis() / 1000;
                    return new long[] {currentTimeSeconds};
                });
    }
}
