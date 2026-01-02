# Required Host Imports for flagd-evaluator WASM Module

The flagd-evaluator WASM module requires **exactly 9 host functions** to be provided. All are correctly implemented in `FlagdWasmRuntime.java`.

## Host Functions Overview

The WASM module imports these functions in the following priority:

### CRITICAL (2 functions) - Required for proper operation

1. **`get_current_time_unix_seconds`** - Provides current Unix timestamp
2. **`getRandomValues`** - Provides cryptographic entropy for hash table seeding

### LEGACY (2 functions) - Kept for backward compatibility

3. **`new_0`** - Creates Date object (legacy timestamp mechanism)
4. **`getTime`** - Gets timestamp from Date (legacy timestamp mechanism)

### ERROR HANDLING (1 function)

5. **`throw`** - Allows WASM to throw exceptions to host

### NO-OPS (4 functions) - wasm-bindgen artifacts

6. **`describe`** - Type description (no-op)
7. **`object_drop_ref`** - Object reference counting (no-op)
8. **`externref_table_grow`** - Externref table growth (no-op)
9. **`externref_table_set_null`** - Externref table null setting (no-op)

## Complete Host Function Reference

| # | Module | Function | Signature | Purpose | Implementation Status |
|---|--------|----------|-----------|---------|----------------------|
| 1 | `host` | `get_current_time_unix_seconds` | `() -> i64` | **CRITICAL:** Provides Unix timestamp for `$flagd.timestamp` context enrichment | ✅ `createGetCurrentTimeUnixSeconds()` |
| 2 | `__wbindgen_placeholder__` | `__wbg_getRandomValues_1c61fac11405ffdc` | `(i32, i32) -> nil` | **CRITICAL:** Provides cryptographic entropy for ahash (used by boon validation) | ✅ `createGetRandomValues()` |
| 3 | `__wbindgen_placeholder__` | `__wbg_new_0_23cedd11d9b40c9d` | `() -> i32` | **LEGACY:** Creates Date object (may be removed in future) | ✅ `createNew0()` |
| 4 | `__wbindgen_placeholder__` | `__wbg_getTime_ad1e9878a735af08` | `(i32) -> f64` | **LEGACY:** Gets timestamp from Date object | ✅ `createGetTime()` |
| 5 | `__wbindgen_placeholder__` | `__wbg___wbindgen_throw_dd24417ed36fc46e` | `(i32, i32) -> nil` | Allows WASM to throw exceptions with error messages | ✅ `createWbindgenThrow()` |
| 6 | `__wbindgen_placeholder__` | `__wbindgen_describe` | `(i32) -> nil` | Type description (wasm-bindgen artifact, no-op) | ✅ `createDescribe()` |
| 7 | `__wbindgen_placeholder__` | `__wbindgen_object_drop_ref` | `(i32) -> nil` | Object reference counting (wasm-bindgen artifact, no-op) | ✅ `createObjectDropRef()` |
| 8 | `__wbindgen_externref_xform__` | `__wbindgen_externref_table_grow` | `(i32) -> i32` | Externref table growth (wasm-bindgen artifact, no-op) | ✅ `createExternrefTableGrow()` |
| 9 | `__wbindgen_externref_xform__` | `__wbindgen_externref_table_set_null` | `(i32) -> nil` | Externref table null setting (wasm-bindgen artifact, no-op) | ✅ `createExternrefTableSetNull()` |

## Implementation Details

### 1. `get_current_time_unix_seconds` - CRITICAL

**Purpose:** Provides the current Unix timestamp for `$flagd.timestamp` context enrichment in targeting rules.

**Implementation:**
```java
private static HostFunction createGetCurrentTimeUnixSeconds() {
    return new HostFunction(
            "host",
            "get_current_time_unix_seconds",
            FunctionType.of(
                    List.of(),
                    List.of(ValType.I64)
            ),
            (Instance instance, long... args) -> {
                long currentTimeSeconds = System.currentTimeMillis() / 1000;
                return new long[] {currentTimeSeconds};
            }
    );
}
```

**Why this matters:**
- Feature flags can use `$flagd.timestamp` in targeting rules for time-based logic
- Without this function, `$flagd.timestamp` defaults to `0`, breaking time-based targeting
- This is the primary mechanism for time access (the Date-based functions are legacy)

### 2. `getRandomValues` - CRITICAL

**Purpose:** Provides cryptographic entropy for `ahash` hash table seeding (used by `boon` JSON schema validation).

**Implementation:**
```java
// Static SecureRandom instance for efficiency (reused across calls)
private static final SecureRandom SECURE_RANDOM = new SecureRandom();

private static HostFunction createGetRandomValues() {
    return new HostFunction(
            "__wbindgen_placeholder__",
            "__wbg_getRandomValues_1c61fac11405ffdc",
            FunctionType.of(
                    List.of(ValType.I32, ValType.I32),
                    List.of()
            ),
            (Instance instance, long... args) -> {
                // CRITICAL: This is called by getrandom to get entropy for ahash
                // ahash uses this for hash table seed generation in boon validation
                int typedArrayPtr = (int) args[0];  // Unused externref handle
                int bufferPtr = (int) args[1];       // Pointer to buffer in WASM memory

                // The WASM code expects a 32-byte buffer at bufferPtr
                // Fill it with cryptographically secure random bytes
                byte[] randomBytes = new byte[32];
                SECURE_RANDOM.nextBytes(randomBytes);

                Memory memory = instance.memory();
                memory.write(bufferPtr, randomBytes);

                return null;
            }
    );
}
```

**Why this matters:**
- The `boon` JSON schema validator uses `ahash` for hash maps
- `ahash` calls `getrandom` to get entropy for hash table seeds
- `getrandom` calls this host function to fill a buffer with random bytes
- **Without proper random bytes, validation will fail or panic**

### 3-4. Date-based Timestamp Functions - LEGACY

**Purpose:** Legacy mechanism for getting timestamps via JavaScript Date objects.

**Status:** These are kept for backward compatibility but are superseded by `get_current_time_unix_seconds`. They may be removed in a future version once the WASM module is updated to only use the direct host function.

**Implementation:**
```java
// Creates a dummy Date object reference
private static HostFunction createNew0() {
    return new HostFunction(
            "__wbindgen_placeholder__",
            "__wbg_new_0_23cedd11d9b40c9d",
            FunctionType.of(List.of(), List.of(ValType.I32)),
            (Instance instance, long... args) -> new long[] {0L}
    );
}

// Returns current time in milliseconds as f64
private static HostFunction createGetTime() {
    return new HostFunction(
            "__wbindgen_placeholder__",
            "__wbg_getTime_ad1e9878a735af08",
            FunctionType.of(List.of(ValType.I32), List.of(ValType.F64)),
            (Instance instance, long... args) -> {
                return new long[] {Double.doubleToRawLongBits((double) System.currentTimeMillis())};
            }
    );
}
```

### 5. `throw` - Error Handling

**Purpose:** Allows the WASM module to throw exceptions with error messages to the Java host.

**Implementation:**
```java
private static HostFunction createWbindgenThrow() {
    return new HostFunction(
            "__wbindgen_placeholder__",
            "__wbg___wbindgen_throw_dd24417ed36fc46e",
            FunctionType.of(List.of(ValType.I32, ValType.I32), List.of()),
            (Instance instance, long... args) -> {
                // Read error message from WASM memory
                int ptr = (int) args[0];
                int len = (int) args[1];
                Memory memory = instance.memory();
                String message = memory.readString(ptr, len);
                throw new RuntimeException("WASM threw: " + message);
            }
    );
}
```

### 6-9. No-op Functions - wasm-bindgen Artifacts

These functions are required by the wasm-bindgen glue code but don't need to do anything in our context:

```java
// All return appropriate no-op values
private static HostFunction createDescribe() { /* returns null */ }
private static HostFunction createObjectDropRef() { /* returns null */ }
private static HostFunction createExternrefTableGrow() { /* returns 128L */ }
private static HostFunction createExternrefTableSetNull() { /* returns null */ }
```

## Verification

You can verify which host functions are actually imported by the WASM module using:

```bash
wasm-objdump -x flagd_evaluator.wasm | grep "Import\[" -A 10
```

This will show exactly 9 imported functions matching the table above.

## Migration Notes

### Removed Functions

The following functions were previously documented but are **NOT** required by the current WASM module and have been removed from `FlagdWasmRuntime.java`:

- `__wbindgen_rethrow`
- `__wbindgen_memory`
- `__wbindgen_is_undefined`
- `__wbindgen_string_new`
- `__wbindgen_number_get`
- `__wbindgen_boolean_get`
- `__wbindgen_is_null`
- `__wbindgen_is_object`
- `__wbindgen_is_string`
- `__wbindgen_object_clone_ref`
- `__wbindgen_jsval_eq`
- `__wbindgen_error_new`

These were wasm-bindgen artifacts that are not actually called by the compiled WASM module.

## Summary

✅ All 9 required host functions are correctly implemented in `FlagdWasmRuntime.java`

✅ The two critical functions (`get_current_time_unix_seconds` and `getRandomValues`) provide essential functionality

✅ The implementation has been cleaned up to include only what's actually needed
