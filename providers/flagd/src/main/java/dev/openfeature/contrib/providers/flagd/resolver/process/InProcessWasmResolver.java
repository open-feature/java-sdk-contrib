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
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.common.FlagdProviderEvent;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageQueryResult;
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
import java.io.IOException;
import java.util.List;
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

    {

        // Register this module with your ObjectMapper
        SimpleModule module = new SimpleModule();
        module.addDeserializer(ImmutableMetadata.class, new ImmutableMetadataDeserializer());
        module.addSerializer(LayeredEvaluationContext.class, new LayeredEvalContextSerializer());
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
    public void shutdown() throws InterruptedException {}

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
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            dealloc.apply(flagPtr, flagBytes.length);
            dealloc.apply(ctxPtr, ctxBytes.length);
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

    // Implement a custom deserializer for ImmutableMetadata
    public class ImmutableMetadataDeserializer extends JsonDeserializer<ImmutableMetadata> {
        @Override
        public ImmutableMetadata deserialize(
                com.fasterxml.jackson.core.JsonParser p, com.fasterxml.jackson.databind.DeserializationContext ctxt)
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

    public class LayeredEvalContextSerializer extends JsonSerializer<LayeredEvaluationContext> {
        @Override
        public void serialize(LayeredEvaluationContext ctx, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeStartObject();

            // Use the keySet and getValue to stream the entries
            for (String key : ctx.keySet()) {
                Object value = ctx.getValue(key);
                gen.writeObjectField(key, value);
            }

            gen.writeEndObject();
        }
    }
}
