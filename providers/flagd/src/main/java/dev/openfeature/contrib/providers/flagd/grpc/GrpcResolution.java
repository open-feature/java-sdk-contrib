package dev.openfeature.contrib.providers.flagd.grpc;

import com.google.protobuf.Descriptors;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.Resolver;
import dev.openfeature.contrib.providers.flagd.cache.Cache;
import dev.openfeature.contrib.providers.flagd.grpc.strategy.ResolveFactory;
import dev.openfeature.contrib.providers.flagd.grpc.strategy.ResolveStrategy;
import dev.openfeature.flagd.grpc.Schema;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Value;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static dev.openfeature.contrib.providers.flagd.Config.CACHED_REASON;
import static dev.openfeature.contrib.providers.flagd.Config.CONTEXT_FIELD;
import static dev.openfeature.contrib.providers.flagd.Config.FLAG_KEY_FIELD;
import static dev.openfeature.contrib.providers.flagd.Config.METADATA_FIELD;
import static dev.openfeature.contrib.providers.flagd.Config.REASON_FIELD;
import static dev.openfeature.contrib.providers.flagd.Config.STATIC_REASON;
import static dev.openfeature.contrib.providers.flagd.Config.VALUE_FIELD;
import static dev.openfeature.contrib.providers.flagd.Config.VARIANT_FIELD;

/**
 * FlagResolution resolves flags from flagd.
 */
@SuppressWarnings("PMD.TooManyStaticImports")
@SuppressFBWarnings(justification = "cache needs to be read and write by multiple objects")
public final class GrpcResolution implements Resolver {

    private final GrpcConnector connector;
    private final Cache cache;
    private final ResolveStrategy strategy;
    private final Supplier<ProviderState> getState;


    /**
     * Initialize the flag resolution.
     *
     * @param cache    cache to use.
     * @param strategy resolution strategy to use.
     * @param getState lambda to call for getting the state.
     */
    public GrpcResolution(final FlagdOptions options, final Cache cache, final Supplier<ProviderState> getState,
                          final Consumer<ProviderState> stateConsumer) {
        this.cache = cache;
        this.getState = getState;

        this.strategy = ResolveFactory.getStrategy(options);
        this.connector = new GrpcConnector(options, cache, stateConsumer);
    }


    public void init() {
        this.connector.initialize();
    }

    public void shutdown() throws Exception {
        this.connector.shutdown();
    }


    public ProviderEvaluation<Boolean> booleanEvaluation(String key, Boolean defaultValue,
                                                         EvaluationContext ctx) {

        Schema.ResolveBooleanRequest request = Schema.ResolveBooleanRequest.newBuilder().buildPartial();

        return resolve(key, ctx, request, this.connector.getResolver()::resolveBoolean, null);
    }

    public ProviderEvaluation<String> stringEvaluation(String key, String defaultValue,
                                                       EvaluationContext ctx) {
        Schema.ResolveStringRequest request = Schema.ResolveStringRequest.newBuilder().buildPartial();

        return resolve(key, ctx, request, this.connector.getResolver()::resolveString, null);
    }

    public ProviderEvaluation<Double> doubleEvaluation(String key, Double defaultValue,
                                                       EvaluationContext ctx) {
        Schema.ResolveFloatRequest request = Schema.ResolveFloatRequest.newBuilder().buildPartial();

        return resolve(key, ctx, request, this.connector.getResolver()::resolveFloat, null);
    }

    public ProviderEvaluation<Integer> integerEvaluation(String key, Integer defaultValue,
                                                         EvaluationContext ctx) {

        Schema.ResolveIntRequest request = Schema.ResolveIntRequest.newBuilder().buildPartial();

        return resolve(key, ctx, request, this.connector.getResolver()::resolveInt,
                (Object value) -> ((Long) value).intValue());
    }

    public ProviderEvaluation<Value> objectEvaluation(String key, Value defaultValue,
                                                      EvaluationContext ctx) {

        Schema.ResolveObjectRequest request = Schema.ResolveObjectRequest.newBuilder().buildPartial();

        return resolve(key, ctx, request, this.connector.getResolver()::resolveObject,
                (Object value) -> convertObjectResponse((com.google.protobuf.Struct) value));
    }


    /**
     * A generic resolve method that takes a resolverRef and an optional converter lambda to transform the result.
     */
    private <ValT, ReqT extends Message, ResT extends Message> ProviderEvaluation<ValT> resolve(
            String key, EvaluationContext ctx, ReqT request, Function<ReqT, ResT> resolverRef,
            Convert<ValT, Object> converter) {

        // return from cache if available and item is present
        if (this.cacheAvailable()) {
            ProviderEvaluation<? extends Object> fromCache = this.cache.get(key);
            if (fromCache != null) {
                fromCache.setReason(CACHED_REASON);
                return (ProviderEvaluation<ValT>) fromCache;
            }
        }

        // build the gRPC request
        Message req = request.newBuilderForType()
                .setField(getFieldDescriptor(request, FLAG_KEY_FIELD), key)
                .setField(getFieldDescriptor(request, CONTEXT_FIELD), this.convertContext(ctx))
                .build();

        // run the referenced resolver method
        Message response = strategy.resolve(resolverRef, req, key);

        // parse the response
        ValT value = converter == null ? getField(response, VALUE_FIELD)
                : converter.convert(getField(response, VALUE_FIELD));

        // Extract metadata from response
        ImmutableMetadata immutableMetadata = metadataFromResponse(response);

        ProviderEvaluation<ValT> result = ProviderEvaluation.<ValT>builder()
                .value(value)
                .variant(getField(response, VARIANT_FIELD))
                .reason(getField(response, REASON_FIELD))
                .flagMetadata(immutableMetadata)
                .build();

        // cache if cache enabled
        if (this.isEvaluationCacheable(result)) {
            this.cache.put(key, result);
        }

        return result;
    }

    private <T> Boolean isEvaluationCacheable(ProviderEvaluation<T> evaluation) {
        String reason = evaluation.getReason();

        return reason != null && reason.equals(STATIC_REASON) && this.cacheAvailable();
    }

    private Boolean cacheAvailable() {
        return this.cache.getEnabled() && ProviderState.READY.equals(this.getState.get());
    }

    /**
     * Recursively convert protobuf structure to openfeature value.
     */
    private static Value convertObjectResponse(Struct protobuf) {
        return convertProtobufMap(protobuf.getFieldsMap());
    }

    /**
     * Recursively convert the Evaluation context to a protobuf structure.
     */
    private static Struct convertContext(EvaluationContext ctx) {
        return convertMap(ctx.asMap()).getStructValue();
    }

    /**
     * Convert any openfeature value to a protobuf value.
     */
    private static com.google.protobuf.Value convertAny(Value value) {
        if (value.isList()) {
            return convertList(value.asList());
        } else if (value.isStructure()) {
            return convertMap(value.asStructure().asMap());
        } else {
            return convertPrimitive(value);
        }
    }

    /**
     * Convert any protobuf value to {@link  Value}.
     */
    private static Value convertAny(com.google.protobuf.Value protobuf) {
        if (protobuf.hasListValue()) {
            return convertList(protobuf.getListValue());
        } else if (protobuf.hasStructValue()) {
            return convertProtobufMap(protobuf.getStructValue().getFieldsMap());
        } else {
            return convertPrimitive(protobuf);
        }
    }

    /**
     * Convert OpenFeature map to protobuf {@link com.google.protobuf.Value}.
     */
    private static com.google.protobuf.Value convertMap(Map<String, Value> map) {
        Map<String, com.google.protobuf.Value> values = new HashMap<>();

        map.keySet().forEach((String key) -> {
            Value value = map.get(key);
            values.put(key, convertAny(value));
        });
        Struct struct = Struct.newBuilder()
                .putAllFields(values).build();
        return com.google.protobuf.Value.newBuilder().setStructValue(struct).build();
    }

    /**
     * Convert protobuf map with {@link com.google.protobuf.Value} to OpenFeature map.
     */
    private static Value convertProtobufMap(Map<String, com.google.protobuf.Value> map) {
        Map<String, Value> values = new HashMap<>();

        map.keySet().forEach((String key) -> {
            com.google.protobuf.Value value = map.get(key);
            values.put(key, convertAny(value));
        });
        return new Value(new MutableStructure(values));
    }

    /**
     * Convert OpenFeature list to protobuf {@link com.google.protobuf.Value}.
     */
    private static com.google.protobuf.Value convertList(List<Value> values) {
        ListValue list = ListValue.newBuilder()
                .addAllValues(values.stream()
                        .map(v -> convertAny(v)).collect(Collectors.toList()))
                .build();
        return com.google.protobuf.Value.newBuilder().setListValue(list).build();
    }

    /**
     * Convert protobuf list to OpenFeature {@link com.google.protobuf.Value}.
     */
    private static Value convertList(ListValue protobuf) {
        return new Value(protobuf.getValuesList().stream().map(p -> convertAny(p)).collect(Collectors.toList()));
    }

    /**
     * Convert OpenFeature {@link Value} to protobuf {@link com.google.protobuf.Value}.
     */
    private static com.google.protobuf.Value convertPrimitive(Value value) {
        com.google.protobuf.Value.Builder builder = com.google.protobuf.Value.newBuilder();

        if (value.isBoolean()) {
            builder.setBoolValue(value.asBoolean());
        } else if (value.isString()) {
            builder.setStringValue(value.asString());
        } else if (value.isNumber()) {
            builder.setNumberValue(value.asDouble());
        } else {
            builder.setNullValue(NullValue.NULL_VALUE);
        }
        return builder.build();
    }

    /**
     * Convert protobuf {@link com.google.protobuf.Value} to OpenFeature  {@link Value}.
     */
    private static Value convertPrimitive(com.google.protobuf.Value protobuf) {
        final Value value;
        if (protobuf.hasBoolValue()) {
            value = new Value(protobuf.getBoolValue());
        } else if (protobuf.hasStringValue()) {
            value = new Value(protobuf.getStringValue());
        } else if (protobuf.hasNumberValue()) {
            value = new Value(protobuf.getNumberValue());
        } else {
            value = new Value();
        }

        return value;
    }

    private static <T> T getField(Message message, String name) {
        return (T) message.getField(getFieldDescriptor(message, name));
    }

    private static Descriptors.FieldDescriptor getFieldDescriptor(Message message, String name) {
        return message.getDescriptorForType().findFieldByName(name);
    }

    private static ImmutableMetadata metadataFromResponse(Message response) {
        final Object metadata = response.getField(getFieldDescriptor(response, METADATA_FIELD));

        if (!(metadata instanceof Struct)) {
            return ImmutableMetadata.builder().build();
        }

        final Struct struct = (Struct) metadata;

        ImmutableMetadata.ImmutableMetadataBuilder builder = ImmutableMetadata.builder();

        for (Map.Entry<String, com.google.protobuf.Value> entry : struct.getFieldsMap().entrySet()) {
            if (entry.getValue().hasStringValue()) {
                builder.addString(entry.getKey(), entry.getValue().getStringValue());
            } else if (entry.getValue().hasBoolValue()) {
                builder.addBoolean(entry.getKey(), entry.getValue().getBoolValue());
            } else if (entry.getValue().hasNumberValue()) {
                builder.addDouble(entry.getKey(), entry.getValue().getNumberValue());
            }
        }

        return builder.build();
    }
}
