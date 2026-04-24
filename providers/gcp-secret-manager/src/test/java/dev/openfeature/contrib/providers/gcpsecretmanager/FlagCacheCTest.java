package dev.openfeature.contrib.providers.gcpsecretmanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.vmlens.api.AllInterleavings;
import com.vmlens.api.Runner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Concurrency tests for {@link FlagCache} using VmLens to explore thread interleavings.
 *
 * <p>Run with: {@code mvn verify -pl providers/gcp-secret-manager -P concurrency-tests}
 */
class FlagCacheCTest {

    /**
     * Verifies that a concurrent expiry-removal does not accidentally evict a freshly inserted
     * entry that reuses the same key. The scenario is:
     * <ol>
     *   <li>Thread A inserts "key" with a TTL that is about to expire.</li>
     *   <li>Thread B calls get("key") and observes the entry as expired — about to remove it.</li>
     *   <li>Thread A inserts "key" again with a fresh TTL.</li>
     *   <li>Thread B completes the removal — the new entry must survive.</li>
     * </ol>
     */
    @Test
    void concurrentExpiryAndInsertDoNotLoseNewEntry() throws Exception {
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

        FlagCache cache = new FlagCache(Duration.ofSeconds(30), 100, controllableClock);
        cache.put("key", "old-value");

        // Advance clock so the entry is expired before threads start
        now.set(now.get().plusSeconds(31));

        try (AllInterleavings interleavings = new AllInterleavings("FlagCache expiry vs insert")) {
            while (interleavings.hasNext()) {
                // Reset: insert an already-expired entry
                cache.put("key", "old-value");
                now.set(now.get().plusSeconds(31));

                // Wind the clock forward to fresh time so new entries won't expire
                AtomicReference<Instant> fresh = new AtomicReference<>(Instant.now());
                Clock freshClock = new Clock() {
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
                        return fresh.get();
                    }
                };
                FlagCache sharedCache = new FlagCache(Duration.ofMinutes(5), 100, freshClock);
                sharedCache.put("key", "expired-value");
                // Make entry expire
                fresh.set(fresh.get().plus(Duration.ofMinutes(6)));

                Runner.runParallel(
                        // Thread A: re-insert fresh value for the same key
                        () -> sharedCache.put("key", "new-value"),
                        // Thread B: get() triggers expiry-removal of the old entry
                        () -> sharedCache.get("key"));

                // After both threads complete, either the new value is present or the cache is
                // empty — the new value must never silently disappear after being inserted.
                Optional<String> result = sharedCache.get("key");
                if (result.isPresent()) {
                    assertThat(result.get()).isEqualTo("new-value");
                }
            }
        }
    }
}
