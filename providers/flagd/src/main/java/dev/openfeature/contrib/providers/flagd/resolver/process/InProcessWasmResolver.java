package dev.openfeature.contrib.providers.flagd.resolver.process;

import static dev.openfeature.contrib.providers.flagd.resolver.common.Convert.convertProtobufMapToStructure;
import static dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag.EMPTY_TARGETING_STRING;

import com.dylibso.chicory.compiler.InterpreterFallback;
import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Machine;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.runtime.Store;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ValType;
import com.dylibso.chicory.wasm.types.ValueType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.common.FlagdProviderEvent;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageQueryResult;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageState;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageStateChange;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.file.FileQueueSource;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.SyncStreamQueueSource;
import dev.openfeature.contrib.providers.flagd.resolver.process.targeting.TargetingRuleException;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ParseError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves flag values using
 * https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1.
 * Flags are evaluated locally.
 */
@Slf4j
public class InProcessWasmResolver implements Resolver {

    private static final Function<Instance, Machine> MACHINE_FUNCTION;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    {

        // Register this module with your ObjectMapper
        SimpleModule module = new SimpleModule();
        module.addDeserializer(ImmutableMetadata.class, new ImmutableMetadataDeserializer());
        OBJECT_MAPPER.registerModule(module);
    }

    private final Consumer<FlagdProviderEvent> onConnectionEvent;
    private final String scope;
    private final QueueSource connector;
    private final ExportFunction validationMode;
    private ExportFunction updateStore;
    private ExportFunction alloc;
    private ExportFunction evaluate;
    private ExportFunction dealloc;
    private Memory memory;
    private static final WasmModule WASM_MODULE;

    static {
        byte[] wasmBytes = null;
        try {
            wasmBytes = Files.readAllBytes(Path.of("flagd_evaluator.wasm"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        WASM_MODULE = Parser.parse(wasmBytes);
        MACHINE_FUNCTION = MachineFactoryCompiler.builder(WASM_MODULE)
                .withInterpreterFallback(InterpreterFallback.WARN)
                .compile();
    }

    /**
     * Resolves flag values using
     * https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1.
     * Flags are evaluated locally.
     *
     * @param options           flagd options
     * @param onConnectionEvent lambda which handles changes in the
     *                          connection/stream
     */
    public InProcessWasmResolver(FlagdOptions options, Consumer<FlagdProviderEvent> onConnectionEvent) {
        this.onConnectionEvent = onConnectionEvent;
        this.connector = getConnector(options, onConnectionEvent);
        this.scope = options.getSelector();

        var store = new Store();
        store.addFunction(
                createDescribe(),
                createExternrefTableGrow(),
                createExternrefTableSetNull(),
                createGetRandomValues(),
                createObjectDropRef(),
                createNew0(),
                createGetTime(),
                createWbindgenThrow(),
                createWbindgenRethrow(),
                createWbindgenMemory(),
                createWbindgenIsUndefined(),
                createWbindgenStringNew(),
                createWbindgenNumberGet(),
                createWbindgenBooleanGet(),
                createWbindgenIsNull(),
                createWbindgenIsObject(),
                createWbindgenIsString(),
                createWbindgenObjectCloneRef(),
                createWbindgenJsvalEq(),
                createWbindgenError()
        );

        var instance = Instance.builder(WASM_MODULE)
                .withImportValues(store.toImportValues())
                .withMachineFactory(
                        MACHINE_FUNCTION)
                .build();
        updateStore = instance.export("update_state");
        evaluate = instance.export("evaluate");
        validationMode = instance.export("set_validation_mode");
        alloc = instance.export("alloc");
        dealloc = instance.export("dealloc");
        validationMode.apply(1);

        memory = instance.memory();
    }

    /**
     * Initialize in-process resolver.
     */
    public void init() throws Exception {

        connector.init();
        final Thread stateWatcher = new Thread(() -> {
            try {
                var streamPayloads = connector.getStreamQueue();
                while (true) {
                    final QueuePayload payload = streamPayloads.take();
                    switch (payload.getType()) {
                        case DATA:
                            List<String> changedFlagsKeys;
                            var data = payload.getFlagData().getBytes();
                            long dataPtr = alloc.apply(data.length)[0];
                            memory.write((int) dataPtr, data);

                            Structure syncContext = parseSyncContext(payload.getSyncContext());
                            long packedResult = updateStore.apply(dataPtr, data.length)[0];

                            int resultPtr = (int) (packedResult >>> 32);
                            int resultLen = (int) (packedResult & 0xFFFFFFFFL);

                            String result = memory.readString(resultPtr, resultLen);

                            dealloc.apply(dataPtr, data.length);

                            var flagsChangedResponse = OBJECT_MAPPER.readValue(result,
                                    FlagsChangedResponse.class);

                            if (flagsChangedResponse.isSuccess()) {
                                onConnectionEvent.accept(new FlagdProviderEvent(
                                        ProviderEvent.PROVIDER_CONFIGURATION_CHANGED,
                                        flagsChangedResponse.getChangedFlags(),
                                        syncContext));
                            }

                            break;
                        case ERROR:

                            break;
                        default:
                            log.warn(String.format("Payload with unknown type: %s", payload.getType()));
                    }
                }
            } catch (InterruptedException e) {
                log.warn("Storage state watcher interrupted", e);
                Thread.currentThread().interrupt();
            } catch (JsonMappingException e) {
                throw new RuntimeException(e);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        stateWatcher.setDaemon(true);
        stateWatcher.start();
    }

    /**
     * Shutdown in-process resolver.
     *
     * @throws InterruptedException if stream can't be closed within deadline.
     */
    public void shutdown() throws InterruptedException {

    }

    /**
     * Resolve a boolean flag.
     */
    public ProviderEvaluation<Boolean> booleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        ProviderEvaluation<Boolean> resolve = resolve(Boolean.class, key, ctx);
        return resolve;
    }

    /**
     * Resolve a string flag.
     */
    public ProviderEvaluation<String> stringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        ProviderEvaluation<String> resolve = resolve(String.class, key, ctx);
        return resolve;
    }

    /**
     * Resolve a double flag.
     */
    public ProviderEvaluation<Double> doubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return resolve(Double.class, key, ctx);
    }

    /**
     * Resolve an integer flag.
     */
    public ProviderEvaluation<Integer> integerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return resolve(Integer.class, key, ctx);
    }

    /**
     * Resolve an object flag.
     */
    public ProviderEvaluation<Value> objectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        final ProviderEvaluation<Object> evaluation = resolve(Object.class, key, ctx);

        return ProviderEvaluation.<Value>builder()
                .value(Value.objectToValue(evaluation.getValue()))
                .variant(evaluation.getVariant())
                .reason(evaluation.getReason())
                .errorCode(evaluation.getErrorCode())
                .errorMessage(evaluation.getErrorMessage())
                .flagMetadata(evaluation.getFlagMetadata())
                .build();
    }

    static QueueSource getConnector(final FlagdOptions options, Consumer<FlagdProviderEvent> onConnectionEvent) {
        if (options.getCustomConnector() != null) {
            return options.getCustomConnector();
        }
        return options.getOfflineFlagSourcePath() != null
                && !options.getOfflineFlagSourcePath().isEmpty()
                ? new FileQueueSource(options.getOfflineFlagSourcePath(), options.getOfflinePollIntervalMs())
                : new SyncStreamQueueSource(options, onConnectionEvent);
    }

    private <T> ProviderEvaluation<T> resolve(Class<T> type, String key, EvaluationContext ctx) {
        byte[] flagBytes = key.getBytes();
        long flagPtr = alloc.apply(flagBytes.length)[0];
        memory.writeString((int) flagPtr, key);

        byte[] ctxBytes = null;
        if (ctx.isEmpty()) {
            ctxBytes = "{}".getBytes();
        } else {
            Map<String, Object> objectMap = ctx.asObjectMap();
            try {
                String s = OBJECT_MAPPER.writeValueAsString(objectMap);
                ctxBytes = s.getBytes();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        long ctxPtr = alloc.apply(ctxBytes.length)[0];
        memory.write((int) ctxPtr, ctxBytes);

        long packedResult = evaluate.apply(flagPtr, flagBytes.length, ctxPtr, ctxBytes.length)[0];
        int resultPtr = (int) (packedResult >>> 32);
        int resultLen = (int) (packedResult & 0xFFFFFFFFL);

        String result = memory.readString(resultPtr, resultLen);

        dealloc.apply(flagPtr, flagBytes.length);
        dealloc.apply(ctxPtr, ctxBytes.length);
        try {
            ProviderEvaluation providerEvaluation = OBJECT_MAPPER.readValue(result, ProviderEvaluation.class);
            return providerEvaluation;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private ImmutableMetadata getFlagMetadata(StorageQueryResult storageQueryResult) {
        ImmutableMetadata.ImmutableMetadataBuilder metadataBuilder = ImmutableMetadata.builder();
        for (Map.Entry<String, Object> entry :
                storageQueryResult.getFlagSetMetadata().entrySet()) {
            addEntryToMetadataBuilder(metadataBuilder, entry.getKey(), entry.getValue());
        }

        if (scope != null) {
            metadataBuilder.addString("scope", scope);
        }

        FeatureFlag flag = storageQueryResult.getFeatureFlag();
        if (flag != null) {
            for (Map.Entry<String, Object> entry : flag.getMetadata().entrySet()) {
                addEntryToMetadataBuilder(metadataBuilder, entry.getKey(), entry.getValue());
            }
        }

        return metadataBuilder.build();
    }

    private void addEntryToMetadataBuilder(
            ImmutableMetadata.ImmutableMetadataBuilder metadataBuilder, String key, Object value) {
        if (value instanceof Number) {
            if (value instanceof Long) {
                metadataBuilder.addLong(key, (Long) value);
                return;
            } else if (value instanceof Double) {
                metadataBuilder.addDouble(key, (Double) value);
                return;
            } else if (value instanceof Integer) {
                metadataBuilder.addInteger(key, (Integer) value);
                return;
            } else if (value instanceof Float) {
                metadataBuilder.addFloat(key, (Float) value);
                return;
            }
        } else if (value instanceof Boolean) {
            metadataBuilder.addBoolean(key, (Boolean) value);
            return;
        } else if (value instanceof String) {
            metadataBuilder.addString(key, (String) value);
            return;
        }
        throw new IllegalArgumentException(
                "The type of the Metadata entry with key " + key + " and value " + value + " is not supported");
    }

    private Structure parseSyncContext(Struct syncContext) {
        if (syncContext != null) {
            try {
                return convertProtobufMapToStructure(syncContext.getFieldsMap());
            } catch (Exception exception) {
                log.error("Failed to parse metadataResponse, provider metadata may not be up-to-date");
            }
        }
        return new ImmutableStructure();
    }


    private static HostFunction createObjectDropRef() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_object_drop_ref",
                FunctionType.of(
                        List.of(ValType.I32),
                        List.of()
                ),
                (Instance instance, long... args) -> {
                    // No-op: We're not tracking JS objects in Java
                    return null;
                }
        );
    }

    private static HostFunction createDescribe() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_describe",
                FunctionType.of(
                        List.of(ValType.I32),
                        List.of()
                ),
                (Instance instance, long... args) -> {
                    // No-op: Type description not needed at runtime
                    return null;
                }
        );
    }

    private static HostFunction createGetRandomValues() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbg_getRandomValues_1c61fac11405ffdc",
                FunctionType.of(
                        List.of(ValType.I32, ValType.I32),
                        List.of()
                ),
                (Instance instance, long... args) -> {

                    return null;
                }
        );
    }

    private static HostFunction createExternrefTableGrow() {
        return new HostFunction(
                "__wbindgen_externref_xform__",
                "__wbindgen_externref_table_grow",
                FunctionType.of(
                        List.of(ValType.I32),
                        List.of(ValType.I32)
                ),
                (Instance instance, long... args) -> {
                    // Return previous size
                    return new long[] {128L};
                }
        );
    }

    private static HostFunction createExternrefTableSetNull() {
        return new HostFunction(
                "__wbindgen_externref_xform__",
                "__wbindgen_externref_table_set_null",
                FunctionType.of(
                        List.of(ValType.I32),
                        List.of()
                ),
                (Instance instance, long... args) -> {
                    // No-op: We don't maintain an externref table
                    return null;
                }
        );
    }

    private static HostFunction createNew0() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbg_new_0_23cedd11d9b40c9d",
                FunctionType.of(
                        List.of(),
                        List.of(ValType.I32)
                ),
                (Instance instance, long... args) -> {
                    // Return a dummy reference
                    return new long[] {0L};
                }
        );
    }

    private static HostFunction createGetTime() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbg_getTime_ad1e9878a735af08",
                FunctionType.of(
                        List.of(ValType.I32),
                        List.of(ValType.F64)
                ),
                (Instance instance, long... args) -> {
                    // Return current time in milliseconds
                    return new long[] {Double.doubleToRawLongBits((double) System.currentTimeMillis())};
                }
        );
    }

    private static HostFunction createWbindgenThrow() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbg___wbindgen_throw_dd24417ed36fc46e",
                FunctionType.of(
                        List.of(ValType.I32, ValType.I32),
                        List.of()
                ),
                (Instance instance, long... args) -> {
                    // Throw exception - read the error message from memory
                    int ptr = (int) args[0];
                    int len = (int) args[1];
                    Memory memory = instance.memory();
                    String message = memory.readString(ptr, len);
                    throw new RuntimeException("WASM threw: " + message);
                }
        );
    }

    private static HostFunction createWbindgenRethrow() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_rethrow",
                FunctionType.of(
                        List.of(ValType.I32),
                        List.of()
                ),
                (Instance instance, long... args) -> {
                    throw new RuntimeException("WASM rethrow");
                }
        );
    }

    private static HostFunction createWbindgenMemory() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_memory",
                FunctionType.of(
                        List.of(),
                        List.of(ValType.I32)
                ),
                (Instance instance, long... args) -> {
                    return new long[] {0L};
                }
        );
    }

    private static HostFunction createWbindgenIsUndefined() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_is_undefined",
                FunctionType.of(
                        List.of(ValType.I32),
                        List.of(ValType.I32)
                ),
                (Instance instance, long... args) -> {
                    return new long[] {0L};
                }
        );
    }

    private static HostFunction createWbindgenStringNew() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_string_new",
                FunctionType.of(
                        List.of(ValType.I32, ValType.I32),
                        List.of(ValType.I32)
                ),
                (Instance instance, long... args) -> {
                    return new long[] {0L};
                }
        );
    }

    private static HostFunction createWbindgenNumberGet() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_number_get",
                FunctionType.of(
                        List.of(ValType.I32, ValType.I32),
                        List.of()
                ),
                (Instance instance, long... args) -> {
                    return null;
                }
        );
    }

    private static HostFunction createWbindgenBooleanGet() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_boolean_get",
                FunctionType.of(
                        List.of(ValType.I32),
                        List.of(ValType.I32)
                ),
                (Instance instance, long... args) -> {
                    return new long[] {0L};
                }
        );
    }

    private static HostFunction createWbindgenIsNull() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_is_null",
                FunctionType.of(
                        List.of(ValType.I32),
                        List.of(ValType.I32)
                ),
                (Instance instance, long... args) -> {
                    return new long[] {0L};
                }
        );
    }

    private static HostFunction createWbindgenIsObject() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_is_object",
                FunctionType.of(
                        List.of(ValType.I32),
                        List.of(ValType.I32)
                ),
                (Instance instance, long... args) -> {
                    return new long[] {0L};
                }
        );
    }

    private static HostFunction createWbindgenIsString() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_is_string",
                FunctionType.of(
                        List.of(ValType.I32),
                        List.of(ValType.I32)
                ),
                (Instance instance, long... args) -> {
                    return new long[] {0L};
                }
        );
    }

    private static HostFunction createWbindgenObjectCloneRef() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_object_clone_ref",
                FunctionType.of(
                        List.of(ValType.I32),
                        List.of(ValType.I32)
                ),
                (Instance instance, long... args) -> {
                    return new long[] {args[0]};
                }
        );
    }

    private static HostFunction createWbindgenJsvalEq() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_jsval_eq",
                FunctionType.of(
                        List.of(ValType.I32, ValType.I32),
                        List.of(ValType.I32)
                ),
                (Instance instance, long... args) -> {
                    return new long[] {args[0] == args[1] ? 1L : 0L};
                }
        );
    }

    private static HostFunction createWbindgenError() {
        return new HostFunction(
                "__wbindgen_placeholder__",
                "__wbindgen_error_new",
                FunctionType.of(
                        List.of(ValType.I32, ValType.I32),
                        List.of(ValType.I32)
                ),
                (Instance instance, long... args) -> {
                    return new long[] {0L};
                }
        );
    }

    // Implement a custom deserializer for ImmutableMetadata
    public class ImmutableMetadataDeserializer extends JsonDeserializer<ImmutableMetadata> {
        @Override
        public ImmutableMetadata deserialize(com.fasterxml.jackson.core.JsonParser p,
                com.fasterxml.jackson.databind.DeserializationContext ctxt)
                throws IOException {
            // Deserialize into a Map or DTO, then use the builder
            Map<String, Object> map = p.readValueAs(Map.class);
            ImmutableMetadata.ImmutableMetadataBuilder builder = ImmutableMetadata.builder();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                builder.addString(entry.getKey(), entry.getValue().toString());
            }
            return builder.build();
        }
    }
}
