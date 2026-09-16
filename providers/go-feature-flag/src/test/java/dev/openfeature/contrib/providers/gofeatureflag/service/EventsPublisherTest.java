package dev.openfeature.contrib.providers.gofeatureflag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventsPublisherTest {
    private static final long FLUSH_INTERVAL_MS = 100L;

    @SneakyThrows
    @DisplayName("a publisher restarted after a shutdown should collect and flush again")
    @Test
    void aPublisherRestartedAfterAShutdownShouldCollectAndFlushAgain() {
        val published = new CopyOnWriteArrayList<String>();
        val publisher = new EventsPublisher<String>(published::addAll, FLUSH_INTERVAL_MS, 10000);

        publisher.add("before-shutdown");
        publisher.shutdown();
        assertEquals(List.of("before-shutdown"), published, "shutdown should drain what is buffered");

        // the provider being initialized again must reset both one-shot pieces of state
        publisher.start();
        publisher.add("after-restart");
        Thread.sleep(FLUSH_INTERVAL_MS * 4);

        assertEquals(
                List.of("before-shutdown", "after-restart"),
                published,
                "a restarted publisher should accept and flush events again");
        assertFalse(publisher.isShutdown.get(), "start() should reset the shutdown flag");
    }

    @SneakyThrows
    @DisplayName("a shut down publisher should drop events until it is restarted")
    @Test
    void aShutDownPublisherShouldDropEventsUntilItIsRestarted() {
        val published = new CopyOnWriteArrayList<String>();
        val publisher = new EventsPublisher<String>(published::addAll, FLUSH_INTERVAL_MS, 10000);

        publisher.shutdown();
        publisher.add("dropped");
        assertTrue(publisher.isShutdown.get(), "the shutdown flag should be raised");

        // restart, so a scheduler exists again: anything accepted while shut down would surface here
        publisher.start();
        Thread.sleep(FLUSH_INTERVAL_MS * 4);

        assertEquals(List.of(), published, "a shut down publisher should not accept new events");
    }

    @SneakyThrows
    @DisplayName("start should be a no-op on a running publisher")
    @Test
    void startShouldBeANoOpOnARunningPublisher() {
        val published = new CopyOnWriteArrayList<String>();
        val publisher = new EventsPublisher<String>(published::addAll, FLUSH_INTERVAL_MS, 10000);

        publisher.start();
        publisher.add("once");
        Thread.sleep(FLUSH_INTERVAL_MS * 4);
        publisher.shutdown();

        assertEquals(List.of("once"), published, "a second scheduler would publish the event twice");
    }

    @SneakyThrows
    @DisplayName("adding an event should not wait for an in-flight publish")
    @Test
    void addingAnEventShouldNotWaitForAnInFlightPublish() {
        val posting = new CountDownLatch(1);
        val releasePost = new CountDownLatch(1);
        val publisher = new EventsPublisher<String>(
                batch -> {
                    posting.countDown();
                    try {
                        releasePost.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                },
                FLUSH_INTERVAL_MS,
                10000);

        publisher.add("first");
        val flush = new Thread(publisher::publish);
        flush.start();
        assertTrue(posting.await(5, TimeUnit.SECONDS), "the publish should have reached the collector");

        // the collector is still hanging: enqueuing must not block behind it
        val added = new CountDownLatch(1);
        new Thread(() -> {
                    publisher.add("while-posting");
                    added.countDown();
                })
                .start();
        assertTrue(added.await(2, TimeUnit.SECONDS), "add() blocked behind the data collector");

        releasePost.countDown();
        flush.join(5000);
        publisher.shutdown();
    }

    @SneakyThrows
    @DisplayName("publishing should be single flight")
    @Test
    void publishingShouldBeSingleFlight() {
        val concurrentPosts = new AtomicInteger();
        val maxConcurrentPosts = new AtomicInteger();
        val posting = new CountDownLatch(1);
        val releasePost = new CountDownLatch(1);
        val publisher = new EventsPublisher<String>(
                batch -> {
                    maxConcurrentPosts.accumulateAndGet(concurrentPosts.incrementAndGet(), Math::max);
                    posting.countDown();
                    try {
                        releasePost.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    concurrentPosts.decrementAndGet();
                },
                FLUSH_INTERVAL_MS,
                10000);

        publisher.add("first");
        val flush = new Thread(publisher::publish);
        flush.start();
        assertTrue(posting.await(5, TimeUnit.SECONDS));

        publisher.add("second");
        publisher.publish();

        releasePost.countDown();
        flush.join(5000);
        assertEquals(1, maxConcurrentPosts.get(), "two publishes overlapped");
        publisher.shutdown();
    }

    @SneakyThrows
    @DisplayName("a failed batch should be re-queued in chronological order")
    @Test
    void aFailedBatchShouldBeRequeuedInChronologicalOrder() {
        val attempts = new CopyOnWriteArrayList<List<String>>();
        val publisher = new EventsPublisher<String>(
                batch -> {
                    attempts.add(List.copyOf(batch));
                    if (attempts.size() == 1) {
                        throw new IllegalStateException("collector is down");
                    }
                },
                FLUSH_INTERVAL_MS,
                10000);

        publisher.add("first");
        publisher.add("second");
        publisher.publish();

        publisher.add("third");
        publisher.publish();

        assertEquals(List.of("first", "second"), attempts.get(0));
        assertEquals(List.of("first", "second", "third"), attempts.get(1), "order was not preserved");
        publisher.shutdown();
    }

    @SneakyThrows
    @DisplayName("the buffer should be capped at twice maxPendingEvents, discarding the oldest")
    @Test
    void theBufferShouldBeCappedAtTwiceMaxPendingEventsDiscardingTheOldest() {
        val attempts = new CopyOnWriteArrayList<List<String>>();
        val collectorIsDown = new AtomicBoolean(true);
        val maxPendingEvents = 4;
        val publisher = new EventsPublisher<String>(
                batch -> {
                    attempts.add(List.copyOf(batch));
                    if (collectorIsDown.get()) {
                        throw new IllegalStateException("collector is down");
                    }
                },
                FLUSH_INTERVAL_MS,
                maxPendingEvents);

        // the collector refuses every batch, so nothing ever leaves the buffer
        for (int i = 0; i < 40; i++) {
            publisher.add("event-" + i);
        }

        collectorIsDown.set(false);
        publisher.publish();

        val delivered = attempts.get(attempts.size() - 1);
        assertEquals(2 * maxPendingEvents, delivered.size(), "the buffer grew past twice maxPendingEvents");
        assertEquals("event-39", delivered.get(delivered.size() - 1), "the newest event should be kept");
        assertEquals("event-32", delivered.get(0), "the oldest events should be the ones discarded");
        publisher.shutdown();
    }
}
