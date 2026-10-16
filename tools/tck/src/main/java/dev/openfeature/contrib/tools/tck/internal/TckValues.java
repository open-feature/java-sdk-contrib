package dev.openfeature.contrib.tools.tck.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.Value;
import java.io.IOException;

/**
 * Converts feature-file strings into typed values; {@code Integer} yields {@link Integer} and
 * {@code Float} yields {@link Double}, with no widening.
 */
public final class TckValues {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TckValues() {}

    /**
     * Converts a feature-file string to a typed value.
     *
     * @param value the raw string from the feature file; the literal {@code null} yields
     *     {@code null}
     * @param type the declared type, one of {@code Boolean}, {@code String}, {@code Integer},
     *     {@code Float} or {@code Object}
     * @return the converted value
     */
    public static Object convert(String value, String type) {
        if ("null".equals(value)) {
            return null;
        }
        switch (type) {
            case "Boolean":
                return Boolean.parseBoolean(value);
            case "String":
                return value;
            case "Integer":
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "'" + value + "' is not a 32-bit Integer; check your extension feature file "
                                    + "(Client.getIntegerDetails cannot request larger values).",
                            e);
                }
            case "Float":
                return Double.parseDouble(value);
            case "Object":
                return toValue(value);
            default:
                throw new IllegalArgumentException("Unknown flag type '" + type
                        + "'. Supported types are Boolean, String, Integer, Float and Object.");
        }
    }

    private static Value toValue(String json) {
        try {
            return Value.objectToValue(MAPPER.readValue(json, Object.class));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not parse '" + json + "' as an Object flag value", e);
        }
    }
}
