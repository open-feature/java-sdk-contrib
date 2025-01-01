package dev.openfeature.contrib.providers.flagd.resolver.grpc.cache;

import static dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.CacheType.DISABLED;
import static dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.CacheType.LRU;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.sdk.ProviderEvaluation;
import org.junit.jupiter.api.Test;

class CacheTest {

    @Test
    void cacheTypeTest() {
        // given
        final Cache disabled = new Cache(DISABLED.getValue(), 0);
        final Cache lru = new Cache(LRU.getValue(), 10);
        final Cache undefined = new Cache("invalid", 10);

        // then
        assertTrue(lru.getEnabled());
        assertFalse(disabled.getEnabled());
        assertFalse(undefined.getEnabled());
    }

    @Test
    void lruOperationValidation() {
        // given
        final Cache lru = new Cache(LRU.getValue(), 1);

        // when
        final ProviderEvaluation<Object> evaluation =
                ProviderEvaluation.builder().value("value").variant("one").build();
        lru.put("key", evaluation);

        // then
        assertEquals(evaluation, lru.get("key"));

        // when
        lru.clear();

        // then
        assertNull(lru.get("key"));
    }
}
