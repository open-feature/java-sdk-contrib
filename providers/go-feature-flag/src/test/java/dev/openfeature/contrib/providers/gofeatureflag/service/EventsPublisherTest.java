package dev.openfeature.contrib.providers.gofeatureflag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
}
