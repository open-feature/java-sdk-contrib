package dev.openfeature.contrib.tools.providertck;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.Value;
import java.io.IOException;

/**
 * Converts the string values written in feature files into the typed Java values the SDK expects.
 *
 * <p>Gherkin has no type system — every cell in an Examples table is a string. The declared flag
 * type in the step is therefore the only thing that distinguishes an integer flag from a float
 * flag, and this class is where that distinction is made real. {@code Integer} produces an
 * {@link Integer}; {@code Float} produces a {@link Double}. Nothing widens one into the other.
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
                return Integer.parseInt(value);
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
