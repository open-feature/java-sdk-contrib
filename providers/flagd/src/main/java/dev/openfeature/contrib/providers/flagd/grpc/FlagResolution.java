package dev.openfeature.contrib.providers.flagd.grpc;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import dev.openfeature.contrib.providers.flagd.cache.Cache;
import dev.openfeature.contrib.providers.flagd.strategy.ResolveStrategy;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Value;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static dev.openfeature.contrib.providers.flagd.Config.CACHED_REASON;
import static dev.openfeature.contrib.providers.flagd.Config.CONTEXT_FIELD;
import static dev.openfeature.contrib.providers.flagd.Config.FLAG_KEY_FIELD;
import static dev.openfeature.contrib.providers.flagd.Config.REASON_FIELD;
import static dev.openfeature.contrib.providers.flagd.Config.STATIC_REASON;
import static dev.openfeature.contrib.providers.flagd.Config.VALUE_FIELD;
import static dev.openfeature.contrib.providers.flagd.Config.VARIANT_FIELD;

/**
 * FlagResolution resolves flags from flagd.
 */
@SuppressWarnings("PMD.TooManyStaticImports")
@SuppressFBWarnings(justification = "cache needs to be read and write by multiple objects")
public final class FlagResolution {

    private final Cache cache;
    private final ResolveStrategy strategy;
    private final Supplier<ProviderState> getState;


    /**
     * Initialize the flag resolution.
     * @param cache cache to use.
     * @param strategy resolution strategy to use.
     * @param getState lambda to call for getting the state.
     */
    public FlagResolution(Cache cache, ResolveStrategy strategy, Supplier<ProviderState> getState) {
        this.cache = cache;
        this.strategy = strategy;
        this.getState = getState;
    }

    /**
     * Recursively convert protobuf structure to openfeature value.
     */
    public Value convertObjectResponse(com.google.protobuf.Struct protobuf) {
        return this.convertProtobufMap(protobuf.getFieldsMap());
    }

    /**
     * Recursively convert the Evaluation context to a protobuf structure.
     */
    private com.google.protobuf.Struct convertContext(EvaluationContext ctx) {
        return this.convertMap(ctx.asMap()).getStructValue();
    }

    /**
     * Convert any openfeature value to a protobuf value.
     */
    private com.google.protobuf.Value convertAny(Value value) {
        if (value.isList()) {
            return this.convertList(value.asList());
        } else if (value.isStructure()) {
            return this.convertMap(value.asStructure().asMap());
        } else {
            return this.convertPrimitive(value);
        }
    }

    /**
     * Convert any protobuf value to an openfeature value.
     */
    private Value convertAny(com.google.protobuf.Value protobuf) {
        if (protobuf.hasListValue()) {
            return this.convertList(protobuf.getListValue());
        } else if (protobuf.hasStructValue()) {
            return this.convertProtobufMap(protobuf.getStructValue().getFieldsMap());
        } else {
            return this.convertPrimitive(protobuf);
        }
    }

    /**
     * Convert openfeature map to protobuf map.
     */
    private com.google.protobuf.Value convertMap(Map<String, Value> map) {
        Map<String, com.google.protobuf.Value> values = new HashMap<>();

        map.keySet().stream().forEach((String key) -> {
            Value value = map.get(key);
            values.put(key, this.convertAny(value));
        });
        com.google.protobuf.Struct struct = com.google.protobuf.Struct.newBuilder()
                .putAllFields(values).build();
        return com.google.protobuf.Value.newBuilder().setStructValue(struct).build();
    }

    /**
     * Convert protobuf map to openfeature map.
     */
    private Value convertProtobufMap(Map<String, com.google.protobuf.Value> map) {
        Map<String, Value> values = new HashMap<>();

        map.keySet().stream().forEach((String key) -> {
            com.google.protobuf.Value value = map.get(key);
            values.put(key, this.convertAny(value));
        });
        return new Value(new MutableStructure(values));
    }

    /**
     * Convert openfeature list to protobuf list.
     */
    private com.google.protobuf.Value convertList(List<Value> values) {
        com.google.protobuf.ListValue list = com.google.protobuf.ListValue.newBuilder()
                .addAllValues(values.stream()
                        .map(v -> this.convertAny(v)).collect(Collectors.toList()))
                .build();
        return com.google.protobuf.Value.newBuilder().setListValue(list).build();
    }

    /**
     * Convert protobuf list to openfeature list.
     */
    private Value convertList(com.google.protobuf.ListValue protobuf) {
        return new Value(protobuf.getValuesList().stream().map(p -> this.convertAny(p)).collect(Collectors.toList()));
    }

    /**
     * Convert openfeature value to protobuf value.
     */
    private com.google.protobuf.Value convertPrimitive(Value value) {
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
     * Convert protobuf value openfeature value.
     */
    private Value convertPrimitive(com.google.protobuf.Value protobuf) {
        Value value;
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

    private <T> Boolean isEvaluationCacheable(ProviderEvaluation<T> evaluation) {
        String reason = evaluation.getReason();

        return reason != null && reason.equals(STATIC_REASON) && this.cacheAvailable();
    }

    private Boolean cacheAvailable() {
        return this.cache.getEnabled() && ProviderState.READY.equals(this.getState.get());
    }

    /**
     * A generic resolve method that takes a resolverRef and an optional converter lambda to transform the result.
     */
    public <ValT, ReqT extends Message, ResT extends Message> ProviderEvaluation<ValT> resolve(
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
        ProviderEvaluation<ValT> result = ProviderEvaluation.<ValT>builder()
                .value(value)
                .variant(getField(response, VARIANT_FIELD))
                .reason(getField(response, REASON_FIELD))
                .build();

        // cache if cache enabled
        if (this.isEvaluationCacheable(result)) {
            this.cache.put(key, result);
        }

        return result;
    }

    private static <T> T getField(Message message, String name) {
        return (T) message.getField(getFieldDescriptor(message, name));
    }

    private static Descriptors.FieldDescriptor getFieldDescriptor(Message message, String name) {
        return message.getDescriptorForType().findFieldByName(name);
    }
}
