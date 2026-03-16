package dev.openfeature.contrib.tools.flagd.api.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.Value;
import java.io.IOException;

/**
 * Type-conversion utilities shared by testkit step definitions.
 */
public final class EvaluatorUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EvaluatorUtils() {}

    /**
     * Converts a string representation of a value to the Java type corresponding
     * to the given flag type name.
     *
     * @param value the string value from the Gherkin table
     * @param type  the flag type name: Boolean, String, Integer, Float, or Object
     * @return the converted value, or {@code null} if {@code value} is "null" or empty for Object
     */
    public static Object convert(String value, String type) throws IOException {
        if (value == null || value.equals("null")) {
            return null;
        }
        switch (type) {
            case "Boolean":
                return Boolean.parseBoolean(value);
            case "String":
                return value;
            case "Integer":
                return value.isEmpty() ? 0 : Integer.parseInt(value);
            case "Float":
                return value.isEmpty() ? 0.0 : Double.parseDouble(value);
            case "Object":
                if (value.isEmpty()) {
                    return Value.objectToValue(MAPPER.readValue("{}", Object.class));
                }
                return Value.objectToValue(MAPPER.readValue(value, Object.class));
            default:
                throw new IllegalArgumentException("Unknown flag type: " + type);
        }
    }

    /**
     * Overload of {@link #convert(String, String)} that accepts a {@link FlagType} enum.
     *
     * @param value    the string value from the Gherkin table
     * @param flagType the flag type enum constant
     * @return the converted value
     */
    public static Object convert(String value, FlagType flagType) throws IOException {
        return convert(value, flagType.getGherkinName());
    }
}
