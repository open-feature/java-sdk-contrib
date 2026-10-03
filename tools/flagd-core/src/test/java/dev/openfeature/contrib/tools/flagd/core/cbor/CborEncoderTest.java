package dev.openfeature.contrib.tools.flagd.core.cbor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for {@link CborEncoder}. Expected outputs are taken directly from
 * RFC 8949 Appendix A ("Examples of Encoded CBOR Data Items") where available,
 * extended with length and map-ordering.
 */
class CborEncoderTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @ParameterizedTest(name = "{0}")
    @MethodSource({"floats", "integers", "strings", "simpleValues", "arrays", "maps"})
    void encodesToExpectedBytes(@SuppressWarnings("unused") String label, JsonNode node, String expectedHex) {
        assertEquals(expectedHex, hex(CborEncoder.encode(node)));
    }

    // Appendix A float examples
    static Stream<Arguments> floats() {
        return Stream.of(
                vector("0.0", NODES.numberNode(0.0), "f90000"),
                vector("-0.0", NODES.numberNode(-0.0), "f98000"),
                vector("1.0", NODES.numberNode(1.0), "f93c00"),
                vector("1.1", NODES.numberNode(1.1), "fb3ff199999999999a"),
                vector("1.5", NODES.numberNode(1.5), "f93e00"),
                vector("65504.0", NODES.numberNode(65504.0), "f97bff"),
                vector("100000.0", NODES.numberNode(100000.0), "fa47c35000"),
                vector("3.4028234663852886e+38", NODES.numberNode(3.4028234663852886e38), "fa7f7fffff"),
                vector("1.0e+300", NODES.numberNode(1.0e300), "fb7e37e43c8800759c"),
                vector("5.960464477539063e-8", NODES.numberNode(5.960464477539063e-8), "f90001"),
                vector("0.00006103515625", NODES.numberNode(0.00006103515625), "f90400"),
                vector("-4.0", NODES.numberNode(-4.0), "f9c400"),
                vector("-4.1", NODES.numberNode(-4.1), "fbc010666666666666"),
                vector("Infinity", NODES.numberNode(Double.POSITIVE_INFINITY), "f97c00"),
                vector("-Infinity", NODES.numberNode(Double.NEGATIVE_INFINITY), "f9fc00"),
                vector("NaN", NODES.numberNode(Double.NaN), "f97e00"));
    }

    // Appendix A integer examples
    static Stream<Arguments> integers() {
        return Stream.of(
                vector("0", NODES.numberNode(0L), "00"),
                vector("1", NODES.numberNode(1L), "01"),
                vector("10", NODES.numberNode(10L), "0a"),
                vector("23", NODES.numberNode(23L), "17"),
                vector("24", NODES.numberNode(24L), "1818"),
                vector("25", NODES.numberNode(25L), "1819"),
                vector("100", NODES.numberNode(100L), "1864"),
                vector("1000", NODES.numberNode(1000L), "1903e8"),
                vector("1000000", NODES.numberNode(1000000L), "1a000f4240"),
                vector("1000000000000", NODES.numberNode(1000000000000L), "1b000000e8d4a51000"),
                vector("Long.MAX_VALUE", NODES.numberNode(Long.MAX_VALUE), "1b7fffffffffffffff"),
                vector("-1", NODES.numberNode(-1L), "20"),
                vector("-10", NODES.numberNode(-10L), "29"),
                vector("-100", NODES.numberNode(-100L), "3863"),
                vector("-1000", NODES.numberNode(-1000L), "3903e7"),
                vector("Long.MIN_VALUE", NODES.numberNode(Long.MIN_VALUE), "3b7fffffffffffffff"),
                // beyond Appendix A: argument-length boundaries (1/2/4/8-byte selection)
                vector("255", NODES.numberNode(255L), "18ff"),
                vector("256", NODES.numberNode(256L), "190100"),
                vector("65535", NODES.numberNode(65535L), "19ffff"),
                vector("65536", NODES.numberNode(65536L), "1a00010000"),
                vector("4294967295", NODES.numberNode(4294967295L), "1affffffff"),
                vector("4294967296", NODES.numberNode(4294967296L), "1b0000000100000000"));
    }

    // Appendix A text-string examples
    static Stream<Arguments> strings() {
        return Stream.of(
                vector("empty", NODES.textNode(""), "60"),
                vector("a", NODES.textNode("a"), "6161"),
                vector("IETF", NODES.textNode("IETF"), "6449455446"),
                vector("quote-backslash", NODES.textNode("\"\\"), "62225c"),
                vector("u00fc", NODES.textNode("\u00fc"), "62c3bc"),
                vector("u6c34", NODES.textNode("\u6c34"), "63e6b0b4"),
                // beyond Appendix A: 24 bytes forces a 1-byte length header
                vector("24-byte string", NODES.textNode("a".repeat(24)), "7818" + "61".repeat(24)));
    }

    static Stream<Arguments> simpleValues() {
        return Stream.of(
                vector("false", NODES.booleanNode(false), "f4"),
                vector("true", NODES.booleanNode(true), "f5"),
                vector("null", NODES.nullNode(), "f6"));
    }

    // Appendix A array examples
    static Stream<Arguments> arrays() {
        ArrayNode empty = NODES.arrayNode();
        ArrayNode flat = NODES.arrayNode();
        flat.add(1).add(2).add(3);
        ArrayNode nested = NODES.arrayNode();
        nested.add(1);
        nested.add(NODES.arrayNode().add(2).add(3));
        nested.add(NODES.arrayNode().add(4).add(5));
        return Stream.of(
                vector("[]", empty, "80"),
                vector("[1,2,3]", flat, "83010203"),
                vector("[1,[2,3],[4,5]]", nested, "8301820203820405"));
    }

    // Appendix A map examples plus canonical key-ordering cases
    static Stream<Arguments> maps() {
        ObjectNode ab = NODES.objectNode();
        ab.put("a", 1).put("b", 2);
        ObjectNode baReversed = NODES.objectNode();
        baReversed.put("b", 2).put("a", 1);
        ObjectNode lengthFirst = NODES.objectNode();
        lengthFirst.put("z", 1).put("aa", 2);
        ObjectNode withArray = NODES.objectNode();
        withArray.put("a", 1).set("b", NODES.arrayNode().add(2).add(3));
        ObjectNode nested = NODES.objectNode();
        nested.set("a", NODES.objectNode().put("b", 1));
        nested.put("b", 2);
        return Stream.of(
                vector("{}", NODES.objectNode(), "a0"),
                vector("{a:1,b:[2,3]}", withArray, "a26161016162820203"),
                // beyond Appendix A: canonical (length-first, then bytewise) key ordering
                vector("{a:1,b:2}", ab, "a2616101616202"),
                vector("{b:2,a:1} reordered", baReversed, "a2616101616202"),
                vector("{z:1,aa:2} length-first", lengthFirst, "a2617a0162616102"),
                vector("{a:{b:1},b:2} nested", nested, "a26161a1616201616202"));
    }

    private static Arguments vector(String label, JsonNode node, String expectedHex) {
        return Arguments.of(Named.of(label, label), node, expectedHex);
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }
}
