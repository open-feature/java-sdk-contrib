package dev.openfeature.contrib.tools.flagd.core.cbor;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Minimal deterministic (RFC 8949 core) CBOR encoder.
 *
 * <p>Covers only the value types a flagd fractional bucketing key can hold: null, boolean, text
 * string, integer, double, array and map. Produces a reproducible byte stream adherent to RFC 8949 for our purposes
 */
public final class CborEncoder {

    private CborEncoder() {}

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

    // canonical map-key ordering: shorter UTF-8 keys first
    private static final Comparator<String> KEY_COMPARATOR = (left, right) -> {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        if (leftBytes.length != rightBytes.length) {
            return Integer.compare(leftBytes.length, rightBytes.length);
        }
        for (int i = 0; i < leftBytes.length; i++) {
            // tiebreak by unsigned bytewise comparison
            int comparison = Integer.compare(leftBytes[i] & BYTE_MASK, rightBytes[i] & BYTE_MASK);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    };

    /**
     * Encodes a JSON node as deterministic CBOR.
     *
     * @param node the value to encode
     * @return the CBOR byte stream
     */
    public static byte[] encode(JsonNode node) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        encodeNode(node, output);
        return output.toByteArray();
    }

    private static void encodeNode(JsonNode node, ByteArrayOutputStream output) {
        if (node.isNull()) {
            output.write(SIMPLE_NULL);
        } else if (node.isBoolean()) {
            output.write(node.asBoolean() ? SIMPLE_TRUE : SIMPLE_FALSE);
        } else if (node.isTextual()) {
            encodeString(node.asText(), output);
        } else if (node.isNumber()) {
            if (node.isBigInteger()) {
                encodeBigInteger(node.bigIntegerValue(), output);
            } else if (node.isIntegralNumber()) {
                encodeLong(node.asLong(), output);
            } else {
                encodeDouble(node.asDouble(), output);
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

    // writes a CBOR head: major type (top 3 bits) + argument (shortest length form); argument is unsigned 64-bit
    private static void writeHead(int majorType, long argument, ByteArrayOutputStream output) {
        int initialByte = majorType << MAJOR_SHIFT;
        if (Long.compareUnsigned(argument, ARG_INLINE_MAX) < 0) {
            output.write(initialByte | (int) argument);
        } else if (Long.compareUnsigned(argument, UINT8_LIMIT) < 0) {
            output.write(initialByte | ARG_ONE_BYTE);
            output.write((int) argument);
        } else if (Long.compareUnsigned(argument, UINT16_LIMIT) < 0) {
            output.write(initialByte | ARG_TWO_BYTES);
            writeBigEndian(argument, 2, output);
        } else if (Long.compareUnsigned(argument, UINT32_LIMIT) < 0) {
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

    // full CBOR integer range [-2^64, 2^64-1]; the low 64 bits are the unsigned argument
    private static void encodeBigInteger(BigInteger value, ByteArrayOutputStream output) {
        if (value.signum() >= 0 && value.bitLength() <= Long.SIZE) {
            writeHead(MAJOR_UNSIGNED_INT, value.longValue(), output);
        } else if (value.signum() < 0 && value.not().bitLength() <= Long.SIZE) {
            writeHead(MAJOR_NEGATIVE_INT, value.not().longValue(), output); // not(n) == -1 - n
        } else {
            throw new IllegalArgumentException("Integer outside CBOR 64-bit range: " + value);
        }
    }

    private static void encodeString(String value, ByteArrayOutputStream output) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeHead(MAJOR_TEXT_STRING, bytes.length, output);
        output.write(bytes, 0, bytes.length);
    }

    // shortest-float: emit float16 if it round-trips exactly, else float32, else float64
    private static void encodeDouble(double value, ByteArrayOutputStream output) {
        if (Double.isNaN(value)) {
            // NaN != NaN, so it can't go through the round-trip check below; emit canonical half nan
            output.write(FLOAT16);
            writeBigEndian(HALF_NAN, 2, output);
            return;
        }
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
            return signBit | HALF_INFINITY; // infinity (nan is handled before this is reached)
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
}
