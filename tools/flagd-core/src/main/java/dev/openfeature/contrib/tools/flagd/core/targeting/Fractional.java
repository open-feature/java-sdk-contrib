package dev.openfeature.contrib.tools.flagd.core.targeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import io.github.jamsesso.jsonlogic.evaluator.expressions.PreEvaluatedArgumentsExpression;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.MurmurHash3;

/**
 * Fractional targeting operation for bucket-based flag distribution.
 */
@Slf4j
class Fractional implements PreEvaluatedArgumentsExpression {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static final int MAX_WEIGHT = Integer.MAX_VALUE;

    @Override
    public String key() {
        return "fractional";
    }

    @Override
    @SuppressWarnings("unchecked") // json-logic-java's PreEvaluatedArgumentsExpression uses raw List
    public Object evaluate(List arguments, Object data, String jsonPath) throws JsonLogicEvaluationException {
        if (arguments.size() < 1) {
            return null;
        }

        final Operator.FlagProperties properties = new Operator.FlagProperties(data);

        final Object bucketBy;
        final List<Object> distributions;

        // json-logic pre-evaluation flattens a single-entry fractional
        // e.g. [["single",1]] becomes ["single", 1]; detect and re-wrap
        if (isFlattened(arguments)) {
            if (properties.getTargetingKey() == null) {
                log.debug("Missing fallback targeting key");
                return null;
            }
            bucketBy = java.util.Arrays.asList(properties.getFlagKey(), properties.getTargetingKey());
            distributions = List.of(arguments);
        } else if (arguments.get(0) instanceof String
                || arguments.get(0) instanceof Boolean
                || arguments.get(0) instanceof Number
                || arguments.get(0) instanceof java.util.Map) {
            // first arg is a primitive or Map, use for bucketing
            bucketBy = arguments.get(0);
            distributions = arguments.subList(1, arguments.size());
        } else {
            // fallback to targeting key if present
            if (properties.getTargetingKey() == null) {
                log.debug("Missing fallback targeting key");
                return null;
            }

            bucketBy = java.util.Arrays.asList(properties.getFlagKey(), properties.getTargetingKey());

            if (arguments.get(0) == null) {
                // arguments.get(0) resolved to null, skip it in distributions
                distributions = arguments.subList(1, arguments.size());
            } else {
                distributions = arguments;
            }
        }

        final List<FractionProperty> propertyList = new ArrayList<>();
        long totalWeight = 0;

        for (Object dist : distributions) {
            try {
                FractionProperty fractionProperty = new FractionProperty(dist, jsonPath);
                propertyList.add(fractionProperty);
                totalWeight += fractionProperty.getWeight();
            } catch (JsonLogicException e) {
                if ("Property is not an array".equals(e.getMessage())) {
                    throw new JsonLogicEvaluationException(
                            "Error parsing fractional targeting rule: " + e.getMessage(), jsonPath);
                }
                return null;
            }
        }

        if (totalWeight > MAX_WEIGHT) {
            log.debug("Total weight {} exceeds maximum allowed value {}", totalWeight, MAX_WEIGHT);
            return null;
        }

        if (totalWeight == 0) {
            log.debug("Total weight is 0, no valid distribution possible");
            return null;
        }

        // find distribution
        return distributeValue(bucketBy, propertyList, (int) totalWeight, jsonPath);
    }

    private static Object distributeValue(
            final Object hashKey,
            final List<FractionProperty> propertyList,
            final int totalWeight,
            final String jsonPath)
            throws JsonLogicEvaluationException {
        byte[] bytes;
        try {
            JsonNode node = OBJECT_MAPPER.valueToTree(hashKey);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            encodeNode(node, out);
            bytes = out.toByteArray();
        } catch (Exception e) {
            log.debug("Error converting hashKey to CBOR", e);
            throw new JsonLogicEvaluationException("Error converting hashKey to CBOR", jsonPath);
        }
        int mmrHash = MurmurHash3.hash32x86(bytes, 0, bytes.length, 0);
        return distributeValueFromHash(mmrHash, propertyList, totalWeight, jsonPath);
    }

    /**
     * Checks if arguments have been flattened by json-logic pre-evaluation.
     * A flattened list contains no List elements (e.g. ["single", 1] instead of [["single", 1]]).
     */
    private static boolean isFlattened(List<?> arguments) {
        for (Object arg : arguments) {
            if (arg instanceof List) {
                return false;
            }
        }
        return true;
    }

    static Object distributeValueFromHash(
            final int hash, final List<FractionProperty> propertyList, final int totalWeight, final String jsonPath)
            throws JsonLogicEvaluationException {
        long longHash = Integer.toUnsignedLong(hash);
        int bucket = (int) ((longHash * totalWeight) >>> 32);

        int bucketSum = 0;
        for (FractionProperty p : propertyList) {
            bucketSum += p.weight;

            if (bucket < bucketSum) {
                return p.getVariant();
            }
        }

        // this shall not be reached
        throw new JsonLogicEvaluationException("Unable to find a correct bucket for hash " + hash, jsonPath);
    }

    private static final Comparator<String> KEY_COMPARATOR = (k1, k2) -> {
        byte[] b1 = k1.getBytes(StandardCharsets.UTF_8);
        byte[] b2 = k2.getBytes(StandardCharsets.UTF_8);
        if (b1.length != b2.length) {
            return Integer.compare(b1.length, b2.length);
        }
        for (int i = 0; i < b1.length; i++) {
            int v1 = b1[i] & 0xFF;
            int v2 = b2[i] & 0xFF;
            if (v1 != v2) {
                return Integer.compare(v1, v2);
            }
        }
        return 0;
    };

    /**
     * CBOR (RFC 8949) encoding constants
     */

    // major types occupy the top 3 bits of the initial byte (value << MAJOR_SHIFT)
    private static final int MAJOR_UNSIGNED_INT = 0;
    private static final int MAJOR_NEGATIVE_INT = 1;
    private static final int MAJOR_TEXT_STRING = 3;
    private static final int MAJOR_ARRAY = 4;
    private static final int MAJOR_MAP = 5;
    private static final int MAJOR_SHIFT = 5;

    // initial-byte low 5 bits: 0-23 inline; 24-27 mean 1/2/4/8 more bytes follow
    private static final int ARG_INLINE_MAX = 24;
    private static final int ARG_ONE_BYTE = 24;
    private static final int ARG_TWO_BYTES = 25;
    private static final int ARG_FOUR_BYTES = 26;
    private static final int ARG_EIGHT_BYTES = 27;

    // thresholds selecting the shortest argument encoding
    private static final long UINT8_LIMIT = 0x100L;
    private static final long UINT16_LIMIT = 0x10000L;
    private static final long UINT32_LIMIT = 0x100000000L;

    // fully-formed initial bytes for major type 7 (simple values + floats)
    private static final int SIMPLE_FALSE = 0xf4;
    private static final int SIMPLE_TRUE = 0xf5;
    private static final int SIMPLE_NULL = 0xf6;
    private static final int FLOAT16 = 0xf9;
    private static final int FLOAT32 = 0xfa;
    private static final int FLOAT64 = 0xfb;

    // float32 fields: sign | 8-bit exponent | 23-bit mantissa
    private static final int FLOAT32_EXPONENT_BIAS = 127;
    private static final int FLOAT32_EXPONENT_MASK = 0xff; // all-ones exponent = inf/nan
    private static final int FLOAT32_MANTISSA_MASK = 0x7fffff; // low 23 bits
    private static final int FLOAT32_MANTISSA_BITS = 23;
    private static final int FLOAT32_IMPLICIT_ONE = 0x800000; // hidden leading 1 (bit 23)

    // half (float16) fields: sign | 5-bit exponent | 10-bit mantissa
    private static final int HALF_SIGN_BIT = 0x8000;
    private static final int FLOAT32_TO_HALF_SIGN_SHIFT = 16; // float bit 31 -> half bit 15
    private static final int HALF_EXPONENT_BIAS = 15;
    private static final int HALF_MANTISSA_BITS = 10;
    private static final int HALF_MANTISSA_MAX = 0x3ff; // 10-bit mantissa (1023)
    private static final int HALF_INFINITY = 0x7c00; // exp all-ones, mantissa 0
    private static final int HALF_NAN = 0x7e00; // canonical quiet nan
    private static final int HALF_MANTISSA_DROP = FLOAT32_MANTISSA_BITS - HALF_MANTISSA_BITS; // 13
    private static final int HALF_MANTISSA_DROP_MASK = (1 << HALF_MANTISSA_DROP) - 1; // low 13 bits
    // unbiased-exponent range representable by a normal half
    private static final int HALF_NORMAL_MIN_EXP = -14;
    private static final int HALF_NORMAL_MAX_EXP = 15;
    private static final int HALF_SUBNORMAL_MIN_EXP = -24;

    private static final int BYTE_MASK = 0xff;
    private static final int BITS_PER_BYTE = 8;
    private static final int NOT_HALF_REPRESENTABLE = -1;

    // minimal deterministic (RFC 8949 core) CBOR encoder covering only the needs of fractional bucketing
    private static void encodeNode(JsonNode node, ByteArrayOutputStream output) {
        if (node.isNull()) {
            output.write(SIMPLE_NULL);
        } else if (node.isBoolean()) {
            output.write(node.asBoolean() ? SIMPLE_TRUE : SIMPLE_FALSE);
        } else if (node.isTextual()) {
            encodeString(node.asText(), output);
        } else if (node.isNumber()) {
            if (node.isIntegralNumber()) {
                encodeLong(node.asLong(), output);
            } else {
                double doubleValue = node.asDouble();
                // normalize whole doubles to integers (matches reference impls)
                if (doubleValue == Math.floor(doubleValue)
                        && doubleValue >= Long.MIN_VALUE
                        && doubleValue <= Long.MAX_VALUE) {
                    encodeLong((long) doubleValue, output);
                } else {
                    encodeDouble(doubleValue, output);
                }
            }
        } else if (node.isArray()) {
            writeHead(MAJOR_ARRAY, node.size(), output);
            for (JsonNode item : node) {
                encodeNode(item, output);
            }
        } else if (node.isObject()) {
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            fieldNames.sort(KEY_COMPARATOR);
            writeHead(MAJOR_MAP, fieldNames.size(), output);
            for (String fieldName : fieldNames) {
                encodeString(fieldName, output);
                encodeNode(node.get(fieldName), output);
            }
        } else {
            throw new IllegalArgumentException("Unsupported node type: " + node.getNodeType());
        }
    }

    // writes a CBOR head: major type (top 3 bits) + argument (shortest length form)
    private static void writeHead(int majorType, long argument, ByteArrayOutputStream output) {
        int initialByte = majorType << MAJOR_SHIFT;
        if (argument < ARG_INLINE_MAX) {
            output.write(initialByte | (int) argument);
        } else if (argument < UINT8_LIMIT) {
            output.write(initialByte | ARG_ONE_BYTE);
            output.write((int) argument);
        } else if (argument < UINT16_LIMIT) {
            output.write(initialByte | ARG_TWO_BYTES);
            writeBigEndian(argument, 2, output);
        } else if (argument < UINT32_LIMIT) {
            output.write(initialByte | ARG_FOUR_BYTES);
            writeBigEndian(argument, 4, output);
        } else {
            output.write(initialByte | ARG_EIGHT_BYTES);
            writeBigEndian(argument, 8, output);
        }
    }

    private static void encodeLong(long value, ByteArrayOutputStream output) {
        if (value >= 0) {
            writeHead(MAJOR_UNSIGNED_INT, value, output);
        } else {
            writeHead(MAJOR_NEGATIVE_INT, -1L - value, output); // negative int argument = -1 - n
        }
    }

    private static void encodeString(String value, ByteArrayOutputStream output) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeHead(MAJOR_TEXT_STRING, bytes.length, output);
        output.write(bytes, 0, bytes.length);
    }

    // shortest-float: emit float16 if it round-trips exactly, else float32, else float64
    private static void encodeDouble(double value, ByteArrayOutputStream output) {
        float floatValue = (float) value;
        if ((double) floatValue == value) {
            int halfBits = floatToHalfExact(floatValue);
            if (halfBits != NOT_HALF_REPRESENTABLE) {
                output.write(FLOAT16);
                writeBigEndian(halfBits, 2, output);
            } else {
                output.write(FLOAT32);
                writeBigEndian(Float.floatToRawIntBits(floatValue), 4, output);
            }
        } else {
            output.write(FLOAT64);
            writeBigEndian(Double.doubleToRawLongBits(value), 8, output);
        }
    }

    // returns 16-bit half bits if the value is EXACTLY representable as IEEE-754 half or NOT_HALF_REPRESENTABLE
    private static int floatToHalfExact(float value) {
        int floatBits = Float.floatToRawIntBits(value);
        int signBit = (floatBits >>> FLOAT32_TO_HALF_SIGN_SHIFT) & HALF_SIGN_BIT; // bit 31 -> bit 15
        int exponent = (floatBits >>> FLOAT32_MANTISSA_BITS) & FLOAT32_EXPONENT_MASK;
        int mantissa = floatBits & FLOAT32_MANTISSA_MASK;
        if (exponent == 0) {
            // +-0 ok; float subnormal too small for half
            return mantissa == 0 ? signBit : NOT_HALF_REPRESENTABLE;
        }
        if (exponent == FLOAT32_EXPONENT_MASK) {
            return mantissa == 0 ? (signBit | HALF_INFINITY) : (signBit | HALF_NAN); // inf / nan
        }
        int powerOfTwo = exponent - FLOAT32_EXPONENT_BIAS;
        if (powerOfTwo >= HALF_NORMAL_MIN_EXP && powerOfTwo <= HALF_NORMAL_MAX_EXP) {
            // half normal
            if ((mantissa & HALF_MANTISSA_DROP_MASK) != 0) {
                return NOT_HALF_REPRESENTABLE; // low mantissa bits would be lost
            }
            return signBit
                    | ((powerOfTwo + HALF_EXPONENT_BIAS) << HALF_MANTISSA_BITS)
                    | (mantissa >> HALF_MANTISSA_DROP);
        }
        if (powerOfTwo >= HALF_SUBNORMAL_MIN_EXP && powerOfTwo < HALF_NORMAL_MIN_EXP) {
            // half subnormal
            int significand = mantissa | FLOAT32_IMPLICIT_ONE; // add implicit leading 1
            int rightShift = -(powerOfTwo + 1); // half = significand * 2^(powerOfTwo+1), exponent < 0
            if ((significand & ((1 << rightShift) - 1)) != 0) {
                return NOT_HALF_REPRESENTABLE;
            }
            int halfBits = significand >> rightShift;
            return (halfBits >= 1 && halfBits <= HALF_MANTISSA_MAX) ? (signBit | halfBits) : NOT_HALF_REPRESENTABLE;
        }
        return NOT_HALF_REPRESENTABLE;
    }

    private static void writeBigEndian(long value, int numBytes, ByteArrayOutputStream output) {
        for (int byteIndex = numBytes - 1; byteIndex >= 0; byteIndex--) {
            output.write((int) ((value >> (BITS_PER_BYTE * byteIndex)) & BYTE_MASK));
        }
    }

    @Getter
    @SuppressWarnings({"checkstyle:NoFinalizer"})
    static class FractionProperty {
        private final Object variant;
        private final int weight;

        protected final void finalize() {
            // DO NOT REMOVE, spotbugs: CT_CONSTRUCTOR_THROW
        }

        FractionProperty(final Object from, String jsonPath) throws JsonLogicException {
            if (!(from instanceof List<?>)) {
                throw new JsonLogicException("Property is not an array", jsonPath);
            }

            final List<?> array = (List) from;

            if (array.isEmpty()) {
                throw new JsonLogicException("Fraction property needs at least one element", jsonPath);
            }

            // variant must be a primitive (string, number, boolean) or null;
            // nested JSONLogic expressions are pre-evaluated to these types
            Object first = array.get(0);
            if (first instanceof String || first instanceof Number || first instanceof Boolean || first == null) {
                variant = first;
            } else {
                throw new JsonLogicException(
                        "First element of the fraction property must resolve to a string, number, boolean, or null",
                        jsonPath);
            }

            if (array.size() >= 2) {
                // weight must be a number
                if (!(array.get(1) instanceof Number)) {
                    throw new JsonLogicException("Second element of the fraction property is not a number", jsonPath);
                }
                Number rawWeight = (Number) array.get(1);

                // weights must be integers
                double weightDouble = rawWeight.doubleValue();
                if (Double.isInfinite(weightDouble)
                        || Double.isNaN(weightDouble)
                        || weightDouble != Math.floor(weightDouble)) {
                    throw new JsonLogicException("Weights must be integers", jsonPath);
                }

                // negative weights can be the result of rollout calculations,
                // so we clamp to 0 rather than throwing an error
                weight = Math.max(0, (int) weightDouble);
            } else {
                weight = 1;
            }
        }
    }
}
