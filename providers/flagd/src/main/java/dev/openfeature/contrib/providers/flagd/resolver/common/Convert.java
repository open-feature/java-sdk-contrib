package dev.openfeature.contrib.providers.flagd.resolver.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.protobuf.Descriptors;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;

/**
 * gRPC type conversion utils.
 */
public class Convert {
    /**
     * Recursively convert protobuf structure to openfeature value.
     */
    public static Value convertObjectResponse(Struct protobuf) {
        return convertProtobufMap(protobuf.getFieldsMap());
    }

    /**
     * Recursively convert the Evaluation context to a protobuf structure.
     */
    public static Struct convertContext(EvaluationContext ctx) {
        Map<String, Value> ctxMap = ctx.asMap();
        // asMap() does not provide explicitly set targeting key (ex:- new
        // ImmutableContext("TargetingKey") ).
        // Hence, we add this explicitly here for targeting rule processing.
        ctxMap.put("targetingKey", new Value(ctx.getTargetingKey()));

        return convertMap(ctxMap).getStructValue();
    }

    /**
     * Convert any openfeature value to a protobuf value.
     */
    public static com.google.protobuf.Value convertAny(Value value) {
        if (value.isList()) {
            return convertList(value.asList());
        } else if (value.isStructure()) {
            return convertMap(value.asStructure().asMap());
        } else {
            return convertPrimitive(value);
        }
    }

    /**
     * Convert any protobuf value to {@link Value}.
     */
    public static Value convertAny(com.google.protobuf.Value protobuf) {
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
    public static com.google.protobuf.Value convertMap(Map<String, Value> map) {
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
     * Convert protobuf map with {@link com.google.protobuf.Value} to OpenFeature
     * map.
     */
    public static Value convertProtobufMap(Map<String, com.google.protobuf.Value> map) {
        return new Value(convertProtobufMapToStructure(map));
    }

    /**
     * Convert protobuf map with {@link com.google.protobuf.Value} to OpenFeature
     * map.
     */
    public static Structure convertProtobufMapToStructure(Map<String, com.google.protobuf.Value> map) {
        Map<String, Value> values = new HashMap<>();

        map.keySet().forEach((String key) -> {
            com.google.protobuf.Value value = map.get(key);
            values.put(key, convertAny(value));
        });
        return new MutableStructure(values);
    }

    /**
     * Convert OpenFeature list to protobuf {@link com.google.protobuf.Value}.
     */
    public static com.google.protobuf.Value convertList(List<Value> values) {
        ListValue list = ListValue.newBuilder()
                .addAllValues(values.stream()
                        .map(v -> convertAny(v)).collect(Collectors.toList()))
                .build();
        return com.google.protobuf.Value.newBuilder().setListValue(list).build();
    }

    /**
     * Convert protobuf list to OpenFeature {@link com.google.protobuf.Value}.
     */
    public static Value convertList(ListValue protobuf) {
        return new Value(protobuf.getValuesList().stream().map(p -> convertAny(p)).collect(Collectors.toList()));
    }

    /**
     * Convert OpenFeature {@link Value} to protobuf
     * {@link com.google.protobuf.Value}.
     */
    public static com.google.protobuf.Value convertPrimitive(Value value) {
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
     * Convert protobuf {@link com.google.protobuf.Value} to OpenFeature
     * {@link Value}.
     */
    public static Value convertPrimitive(com.google.protobuf.Value protobuf) {
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

    /**
     * Get the specified protobuf field from the message.
     * 
     * @param <T> type
     * @param message protobuf message
     * @param name    field name
     * @return field value
     */
    public static <T> T getField(Message message, String name) {
        return (T) message.getField(getFieldDescriptor(message, name));
    }

    /**
     * Get the specified protobuf field descriptor from the message.
     * 
     * @param message protobuf message
     * @param name    field name
     * @return field descriptor
     */
    public static Descriptors.FieldDescriptor getFieldDescriptor(Message message, String name) {
        return message.getDescriptorForType().findFieldByName(name);
    }
}
