package dev.openfeature.contrib.providers.gcpsecretmanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.vmlens.api.AllInterleavings;
import com.vmlens.api.Runner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Concurrency tests for {@link FlagCache} using VmLens to explore thread interleavings.
 *
 * <p>Run with: {@code mvn verify -pl providers/gcp-secret-manager -P concurrency-tests}
 */
class FlagCacheCTest {

    // -------------------------------------------------------------------------
    // Shared state for getOnTimedOutEntryWhileConcurrentInsertNeverReturnsStaleValue
    // -------------------------------------------------------------------------

    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(31);

    private AtomicReference<Instant> now;
    private FlagCache cache;

    @BeforeEach
    void setUp() {
        now = new AtomicReference<>(T0);
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
        cache = new FlagCache(Duration.ofSeconds(30), 100, clock);
    }

    /**
     * Verifies that a concurrent expiry-removal does not accidentally evict a freshly inserted
     * entry that reuses the same key. The scenario is:
     * <ol>
     *   <li>An entry for "key" exists with a TTL that has already elapsed.</li>
     *   <li>Thread A re-inserts "key" with a fresh value while the clock is past the old TTL.</li>
     *   <li>Thread B calls get("key"), observes the old entry as expired, and removes it.</li>
     *   <li>After both threads finish, "new-value" must still be present — Thread B's stale
     *       removal must not accidentally evict the entry written by Thread A.</li>
     * </ol>
     */
    @Test
    void concurrentExpiryAndInsertDoNotLoseNewEntry() throws Exception {
        try (AllInterleavings interleavings =
                new AllInterleavings("FlagCache concurrent expiry and re-insert")) {
            while (interleavings.hasNext()) {
                // Reset state for this interleaving:
                //   - clock at T0 so the inserted entry records expiresAt = T0 + 30 s
                now.set(T0);
                cache.clear();
                cache.put("key", "expired-value");
                //   - advance clock to T1 so the entry is now expired
                now.set(T1);

                Runner.runParallel(
                        // Thread A: re-insert the same key with a fresh value
                        () -> cache.put("key", "new-value"),
                        // Thread B: get() detects the stale entry and attempts removal
                        () -> cache.get("key"));

                // Thread A's put() has returned, so "new-value" must be in the cache.
                // Thread B's expiry-removal must not have silently evicted it.
                assertThat(cache.get("key")).isPresent().hasValue("new-value");
            }
        }
    }

    /**
     * Verifies that while a timed-out entry is being read and a concurrent insert of the same key
     * is ongoing, {@code get} never surfaces the stale value — it returns either nothing (expired
     * entry removed before the insert) or the freshly inserted value (insert won the race).
     *
     * <p>Follows the VmLens pattern from {@code FlagdProviderCTest}: shared state is prepared once
     * before the interleaving loop; only {@link Runner#runParallel} lives inside the loop; and
     * assertions are embedded in the parallel lambdas so VmLens evaluates them under every
     * explored scheduling.
     */
    @Test
    void getOnTimedOutEntryWhileConcurrentInsertNeverReturnsStaleValue() throws Exception {
        // Prepare a single expired entry once — the clock stays at T1 for all interleavings
        cache.put("key", "stale-value");
        now.set(T1);

        try (var interleavings =
                new AllInterleavings("FlagCache: get on timed-out entry concurrent with insert")) {
            while (interleavings.hasNext()) {
                Runner.runParallel(
                        // Thread A: insert a fresh value for the same key
                        () -> cache.put("key", "new-value"),
                        // Thread B: get() on the timed-out entry must return nothing
                        // (expired entry removed) or "new-value" (Thread A won the race) —
                        // never the stale "stale-value" whose TTL has elapsed
                        () -> assertThat(cache.get("key")).satisfiesAnyOf(
                                opt -> assertThat(opt).isEmpty(),
                                opt -> assertThat(opt).hasValue("new-value")));
            }
        }
    }
}
