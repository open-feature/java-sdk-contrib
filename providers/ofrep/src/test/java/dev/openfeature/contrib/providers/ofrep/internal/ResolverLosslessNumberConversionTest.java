package dev.openfeature.contrib.providers.ofrep.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ResolverLosslessNumberConversionTest {

    @Test
    void widensIntegersToDouble() {
        assertEquals(10.0, Resolver.convertNumberIfLossless(Double.class, 10));
        assertEquals(4_000_000_000.0, Resolver.convertNumberIfLossless(Double.class, 4_000_000_000L));
    }

    @Test
    void keepsLongsThatDoubleCannotRepresent() {
        long notExact = (1L << 53) + 1;
        assertEquals(notExact, Resolver.convertNumberIfLossless(Double.class, notExact));
        assertEquals(Long.MAX_VALUE, Resolver.convertNumberIfLossless(Double.class, Long.MAX_VALUE));
    }

    @Test
    void narrowsIntegralNumbersToInteger() {
        assertEquals(10, Resolver.convertNumberIfLossless(Integer.class, 10.0));
        assertEquals(-3, Resolver.convertNumberIfLossless(Integer.class, -3.0));
        assertEquals(7, Resolver.convertNumberIfLossless(Integer.class, 7L));
    }

    @Test
    void keepsValuesThatWouldLoseInformation() {
        assertEquals(0.5, Resolver.convertNumberIfLossless(Integer.class, 0.5));
        assertEquals(3e10, Resolver.convertNumberIfLossless(Integer.class, 3e10));
        assertEquals(5_000_000_000L, Resolver.convertNumberIfLossless(Integer.class, 5_000_000_000L));
    }

    @Test
    void leavesOtherTypesAlone() {
        assertEquals(1.5, Resolver.convertNumberIfLossless(Double.class, 1.5));
        assertEquals("10", Resolver.convertNumberIfLossless(Integer.class, "10"));
        assertEquals(10, Resolver.convertNumberIfLossless(String.class, 10));
        assertEquals(10, Resolver.convertNumberIfLossless(Boolean.class, 10));
    }
}
