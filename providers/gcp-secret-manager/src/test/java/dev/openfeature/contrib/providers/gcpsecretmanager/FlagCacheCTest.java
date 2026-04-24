package dev.openfeature.contrib.providers.gcpsecretmanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.vmlens.api.AllInterleavings;
import com.vmlens.api.Runner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
     *   <li>An entry for "key" is inserted with a TTL that has already elapsed.</li>
     *   <li>Thread A re-inserts "key" with a fresh TTL ("new-value").</li>
     *   <li>Thread B calls get("key"), observes the original entry as expired, and removes it.</li>
     *   <li>After both threads finish, "new-value" must still be present — Thread B's removal
     *       must not accidentally evict the entry written by Thread A.</li>
     * </ol>
     */
    @Test
    void concurrentExpiryAndInsertDoNotLoseNewEntry() throws Exception {
        try (AllInterleavings interleavings =
                new AllInterleavings("FlagCache expiry vs insert")) {
            while (interleavings.hasNext()) {
                AtomicReference<Instant> now =
                        new AtomicReference<>(Instant.parse("2024-01-01T00:00:00Z"));
                Clock clock = new Clock() {
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

                FlagCache cache = new FlagCache(Duration.ofSeconds(30), 100, clock);
                cache.put("key", "expired-value");

                // Advance clock past TTL so "expired-value" is expired when threads start
                now.set(now.get().plusSeconds(31));

                Runner.runParallel(
                        // Thread A: re-insert a fresh value under the same key
                        () -> cache.put("key", "new-value"),
                        // Thread B: get() finds expired entry and attempts removal
                        () -> cache.get("key"));

                // Thread A's put() has completed, so "new-value" must be present.
                // Thread B's expiry-removal must not have evicted Thread A's new entry.
                assertThat(cache.get("key")).isPresent().hasValue("new-value");
            }
        }
    }
}
