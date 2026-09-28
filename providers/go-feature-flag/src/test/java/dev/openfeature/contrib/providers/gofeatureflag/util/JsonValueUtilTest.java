package dev.openfeature.contrib.providers.gofeatureflag.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonValueUtilTest {
    private static final BigInteger BEYOND_LONG = new BigInteger("18446744073709551616");

    @Test
    void testWidenBigIntegers_TopLevel() {
        assertEquals(18446744073709551616.0, JsonValueUtil.widenBigIntegers(BEYOND_LONG));
    }

    @Test
    void testWidenBigIntegers_Nested() {
        Object widened = JsonValueUtil.widenBigIntegers(
                Map.of("limit", BEYOND_LONG, "tiers", List.of(Map.of("max", BEYOND_LONG))));

        assertEquals(
                Map.of("limit", 18446744073709551616.0, "tiers", List.of(Map.of("max", 18446744073709551616.0))),
                widened);
    }

    @Test
    void testWidenBigIntegers_OtherValuesUnchanged() {
        assertEquals(42L, JsonValueUtil.widenBigIntegers(42L));
        assertEquals("text", JsonValueUtil.widenBigIntegers("text"));
        assertEquals(List.of(1, true, "a"), JsonValueUtil.widenBigIntegers(List.of(1, true, "a")));
        assertNull(JsonValueUtil.widenBigIntegers(null));
    }
}
