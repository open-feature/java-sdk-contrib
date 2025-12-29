package dev.openfeature.contrib.providers.flagd.resolver.process;

import static dev.openfeature.contrib.providers.flagd.resolver.common.Convert.convertProtobufMapToStructure;
import static dev.openfeature.contrib.providers.flagd.resolver.process.FlagdWasmRuntime.createStoreWithHostFunctions;
import static dev.openfeature.contrib.providers.flagd.resolver.process.FlagdWasmRuntime.getMachineFunction;
import static dev.openfeature.contrib.providers.flagd.resolver.process.FlagdWasmRuntime.getModule;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ValType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.common.FlagdProviderEvent;
import dev.openfeature.contrib.providers.flagd.resolver.process.jackson.ImmutableMetadataDeserializer;
import dev.openfeature.contrib.providers.flagd.resolver.process.jackson.LayeredEvalContextSerializer;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.file.FileQueueSource;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.SyncStreamQueueSource;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.LayeredEvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves flag values using
 * https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1.
 * Flags are evaluated locally.
 */
@Slf4j
public class InProcessWasmResolver implements Resolver {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        // Register custom serializers/deserializers with the ObjectMapper
        SimpleModule module = new SimpleModule();
        module.addDeserializer(ImmutableMetadata.class, new ImmutableMetadataDeserializer());
        module.addSerializer(LayeredEvaluationContext.class, new LayeredEvalContextSerializer());
        OBJECT_MAPPER.registerModule(module);
    }

    private final Consumer<FlagdProviderEvent> onConnectionEvent;
    private final String scope;
    private final QueueSource connector;
    private Thread stateWatcher;
    private final ExportFunction validationMode;
    private ExportFunction updateStore;
    private ExportFunction alloc;
    private ExportFunction evaluate;
    private ExportFunction dealloc;
    private Memory memory;

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

        var store = createStoreWithHostFunctions();

        var instance = Instance.builder(getModule())
                .withImportValues(store.toImportValues())
                .withMachineFactory(getMachineFunction())
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
        this.stateWatcher = new Thread(() -> {
            var streamPayloads = connector.getStreamQueue();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final QueuePayload payload = streamPayloads.take();
                    switch (payload.getType()) {
                        case DATA:
                            var data = payload.getFlagData().getBytes();
                            long dataPtr = alloc.apply(data.length)[0];
                            memory.write((int) dataPtr, data);

                            Structure syncContext = parseSyncContext(payload.getSyncContext());
                            long packedResult = updateStore.apply(dataPtr, data.length)[0];

                            int resultPtr = (int) (packedResult >>> 32);
                            int resultLen = (int) (packedResult & 0xFFFFFFFFL);

                            String result = memory.readString(resultPtr, resultLen);

                            dealloc.apply(dataPtr, data.length);

                            var flagsChangedResponse = OBJECT_MAPPER.readValue(result, FlagsChangedResponse.class);

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
                } catch (InterruptedException e) {
                    log.debug("Storage state watcher interrupted", e);
                    Thread.currentThread().interrupt();
                    break;
                } catch (JsonProcessingException e) {
                    log.error("Error processing flag data, skipping update", e);
                }
            }
        });
        this.stateWatcher.setDaemon(true);
        this.stateWatcher.start();
    }

    /**
     * Shutdown in-process resolver.
     *
     * @throws InterruptedException if stream can't be closed within deadline.
     */
    public void shutdown() throws InterruptedException {
        if (stateWatcher != null) {
            stateWatcher.interrupt();
        }
        connector.shutdown();
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

        String ctxJson = "{}";
        if (ctx.isEmpty()) {
        } else {
            Map<String, Object> objectMap = ctx.asObjectMap();
            try {
                ctxJson = OBJECT_MAPPER.writeValueAsString(objectMap);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        byte[] ctxBytes = ctxJson.getBytes();
        long ctxPtr = alloc.apply(ctxBytes.length)[0];
        memory.write((int) ctxPtr, ctxBytes);

        try {
            long packedResult = evaluate.apply(flagPtr, flagBytes.length, ctxPtr, ctxBytes.length)[0];
            int resultPtr = (int) (packedResult >>> 32);
            int resultLen = (int) (packedResult & 0xFFFFFFFFL);

            String result = memory.readString(resultPtr, resultLen);
            JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructParametricType(ProviderEvaluation.class, type);
            ProviderEvaluation<T> providerEvaluation = OBJECT_MAPPER.readValue(result, javaType);

            return providerEvaluation;
        } catch (JsonProcessingException e) {
            throw new GeneralError("Error deserializing WASM evaluation result", e);
        } catch (Exception e) {
            throw new GeneralError("Error during WASM evaluation", e);
        } finally {
            dealloc.apply(flagPtr, flagBytes.length);
            dealloc.apply(ctxPtr, ctxBytes.length);
        }
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
}
