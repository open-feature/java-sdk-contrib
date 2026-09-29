package dev.openfeature.contrib.providers.gofeatureflag.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * JsonValueUtil is a utility class to prepare values decoded from JSON before they are converted to
 * an Open Feature Value.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JsonValueUtil {
    /**
     * widenBigIntegers replaces every BigInteger in a decoded JSON value with its Double equivalent.
     *
     * <p>Jackson decodes an integer beyond the long range as BigInteger, which Value.objectToValue
     * rejects. The engine writes those numbers from a float64, so a Double holds them exactly.
     *
     * @param value - a value decoded from the engine's JSON output
     * @return the same value, with BigInteger replaced by Double at any depth
     */
    public static Object widenBigIntegers(final Object value) {
        if (value instanceof BigInteger) {
            return ((BigInteger) value).doubleValue();
        }
        if (value instanceof Map) {
            Map<Object, Object> widened = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                widened.put(entry.getKey(), widenBigIntegers(entry.getValue()));
            }
            return widened;
        }
        if (value instanceof List) {
            List<Object> widened = new ArrayList<>();
            for (Object item : (List<?>) value) {
                widened.add(widenBigIntegers(item));
            }
            return widened;
        }
        return value;
    }
}
