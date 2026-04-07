package dev.openfeature.contrib.providers.gcpsecretmanager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.ParseError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts raw string secret values (fetched from GCP Secret Manager) into the
 * typed values expected by the OpenFeature SDK evaluation methods.
 */
@Slf4j
final class FlagValueConverter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FlagValueConverter() {}

    /**
     * Converts a raw string value to the given target type.
     *
     * <p>Supported conversions:
     * <ul>
     *   <li>{@code Boolean.class}: "true"/"false" (case-insensitive)</li>
     *   <li>{@code Integer.class}: numeric string</li>
     *   <li>{@code Double.class}: numeric string</li>
     *   <li>{@code String.class}: raw string as-is</li>
     *   <li>{@code Value.class}: JSON string parsed to {@link Value}/{@link dev.openfeature.sdk.Structure};
     *       non-JSON strings are wrapped in a string {@link Value}</li>
     * </ul>
     *
     * @param raw        the raw string value from GCP Secret Manager
     * @param targetType the desired OpenFeature type
     * @param <T>        the target type
     * @return the converted value
     * @throws ParseError       when the string cannot be parsed into the expected type
     * @throws TypeMismatchError when the runtime type of the parsed value does not match
     */
    @SuppressWarnings("unchecked")
    static <T> T convert(String raw, Class<T> targetType) {
        if (raw == null) {
            throw new ParseError("Flag value is null");
        }

        if (targetType == Boolean.class) {
            return (T) convertBoolean(raw);
        }
        if (targetType == Integer.class) {
            return (T) convertInteger(raw);
        }
        if (targetType == Double.class) {
            return (T) convertDouble(raw);
        }
        if (targetType == String.class) {
            return (T) raw;
        }
        if (targetType == Value.class) {
            return (T) convertValue(raw);
        }

        throw new TypeMismatchError("Unsupported target type: " + targetType.getName());
    }

    private static Boolean convertBoolean(String raw) {
        String trimmed = raw.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return Boolean.FALSE;
        }
        throw new ParseError("Cannot convert '" + raw + "' to Boolean. Expected \"true\" or \"false\".");
    }

    private static Integer convertInteger(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new ParseError("Cannot convert '" + raw + "' to Integer: " + e.getMessage(), e);
        }
    }

    private static Double convertDouble(String raw) {
        try {
            return Double.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new ParseError("Cannot convert '" + raw + "' to Double: " + e.getMessage(), e);
        }
    }

    private static Value convertValue(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(trimmed);
                return jsonNodeToValue(node);
            } catch (JsonProcessingException e) {
                log.debug("Value '{}' is not valid JSON, wrapping as plain string", raw);
            }
        }
        return new Value(raw);
    }

    /**
     * Recursively converts a Jackson {@link JsonNode} to an OpenFeature {@link Value}.
     */
    static Value jsonNodeToValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return new Value();
        }
        if (node.isBoolean()) {
            return new Value(node.booleanValue());
        }
        if (node.isInt()) {
            return new Value(node.intValue());
        }
        if (node.isDouble() || node.isFloat()) {
            return new Value(node.doubleValue());
        }
        if (node.isNumber()) {
            return new Value(node.doubleValue());
        }
        if (node.isTextual()) {
            return new Value(node.textValue());
        }
        if (node.isObject()) {
            return new Value(objectNodeToStructure((ObjectNode) node));
        }
        if (node.isArray()) {
            return new Value(arrayNodeToList((ArrayNode) node));
        }
        return new Value(node.toString());
    }

    private static MutableStructure objectNodeToStructure(ObjectNode objectNode) {
        MutableStructure structure = new MutableStructure();
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            structure.add(field.getKey(), jsonNodeToValue(field.getValue()));
        }
        return structure;
    }

    private static List<Value> arrayNodeToList(ArrayNode arrayNode) {
        List<Value> list = new ArrayList<>(arrayNode.size());
        for (JsonNode element : arrayNode) {
            list.add(jsonNodeToValue(element));
        }
        return list;
    }
}
