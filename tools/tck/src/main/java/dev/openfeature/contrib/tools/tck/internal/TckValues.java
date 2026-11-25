package dev.openfeature.contrib.tools.tck.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.Value;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

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
     *     {@code Long}, {@code Float}, {@code StringList} or {@code Object}
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
            case "Long":
                return Long.parseLong(value);
            case "Float":
                return Double.parseDouble(value);
            case "StringList":
                // a context key an operator reads as a list; empty means the empty list, not [""]
                return value.isEmpty()
                        ? Collections.emptyList()
                        : Arrays.stream(value.split(",")).map(String::trim).collect(Collectors.toList());
            case "Object":
                return toValue(value);
            default:
                throw new IllegalArgumentException("Unknown flag type '" + type
                        + "'. Supported types are Boolean, String, Integer, Long, Float, StringList "
                        + "and Object.");
        }
    }

    /**
     * Converts a feature-file string for an <em>evaluation context</em> entry.
     *
     * <p>Differs from {@link #convert} in one place, and deliberately: an {@code Integer} too wide
     * for 32 bits widens to a {@code long} instead of failing. A flag's default value is bounded by
     * the accessor that reads it back -- {@code getIntegerDetails} cannot ask for more -- but nothing
     * reads a context entry back through an accessor, and a targeting operator may hash a 64-bit
     * number. Rejecting those would make the TCK refuse context a provider handles correctly.
     *
     * @param value the raw string from the feature file
     * @param type the declared type, as {@link #convert} takes it
     * @return the converted value
     */
    public static Object convertContextEntry(String value, String type) {
        if ("Integer".equals(type) && !"null".equals(value)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException wider) {
                return Long.parseLong(value);
            }
        }
        return convert(value, type);
    }

    private static Value toValue(String json) {
        try {
            return Value.objectToValue(MAPPER.readValue(json, Object.class));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not parse '" + json + "' as an Object flag value", e);
        }
    }
}
