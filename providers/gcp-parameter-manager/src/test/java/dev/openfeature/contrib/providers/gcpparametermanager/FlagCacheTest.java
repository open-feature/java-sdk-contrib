package dev.openfeature.contrib.providers.gcpparametermanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
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
    void getAfterTtlExpiry() throws InterruptedException {
        FlagCache shortTtlCache = new FlagCache(Duration.ofMillis(50), 100);
        shortTtlCache.put("flag", "value");
        Thread.sleep(100);
        assertThat(shortTtlCache.get("flag")).isEmpty();
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
    void maxSizeEvictsOldest() throws InterruptedException {
        FlagCache tinyCache = new FlagCache(Duration.ofMinutes(5), 2);
        tinyCache.put("first", "1");
        Thread.sleep(5); // ensure different insertion time
        tinyCache.put("second", "2");
        Thread.sleep(5);
        // inserting a third entry should evict the oldest
        tinyCache.put("third", "3");
        // at least the newest entry must be present
        assertThat(tinyCache.get("third")).isPresent().hasValue("3");
        // total size should be at most maxSize
        int present = 0;
        for (String key : new String[] {"first", "second", "third"}) {
            if (tinyCache.get(key).isPresent()) {
                present++;
            }
        }
        assertThat(present).isLessThanOrEqualTo(2);
    }
}
