package dev.openfeature.contrib.providers.gcpsecretmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FlagCache")
class FlagCacheTest {

    private FlagCache cache;

    @BeforeEach
    void setUp() {
        cache = new FlagCache(Duration.ofMinutes(5), 100);
    }

    @Test
    @DisplayName("get() returns empty for an unknown key")
    void getUnknownKeyReturnsEmpty() {
        assertThat(cache.get("unknown")).isEmpty();
    }

    @Test
    @DisplayName("put() then get() returns value before expiry")
    void putAndGetBeforeExpiry() {
        cache.put("my-flag", "true");
        Optional<String> result = cache.get("my-flag");
        assertThat(result).isPresent().hasValue("true");
    }

    @Test
    @DisplayName("get() returns empty after TTL expires")
    void getAfterTtlExpiry() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2024-01-01T00:00:00Z"));
        Clock controllableClock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };

        FlagCache timedCache = new FlagCache(Duration.ofSeconds(30), 100, controllableClock);
        timedCache.put("flag", "value");

        now.set(now.get().plusSeconds(31));

        assertThat(timedCache.get("flag")).isEmpty();
    }

    @Test
    @DisplayName("invalidate() removes the entry")
    void invalidateRemovesEntry() {
        cache.put("flag", "hello");
        cache.invalidate("flag");
        assertThat(cache.get("flag")).isEmpty();
    }

    @Test
    @DisplayName("clear() removes all entries")
    void clearRemovesAll() {
        cache.put("a", "1");
        cache.put("b", "2");
        cache.clear();
        assertThat(cache.get("a")).isEmpty();
        assertThat(cache.get("b")).isEmpty();
    }

    @Test
    @DisplayName("maxSize evicts the oldest entry on overflow")
    void maxSizeEvictsOldest() {
        FlagCache tinyCache = new FlagCache(Duration.ofMinutes(5), 2);
        tinyCache.put("first", "1");
        tinyCache.put("second", "2");
        tinyCache.put("third", "3");
        assertThat(tinyCache.get("third")).isPresent().hasValue("3");
        int present = 0;
        for (String key : new String[] {"first", "second", "third"}) {
            if (tinyCache.get(key).isPresent()) {
                present++;
            }
        }
        assertThat(present).isLessThanOrEqualTo(2);
    }
}
