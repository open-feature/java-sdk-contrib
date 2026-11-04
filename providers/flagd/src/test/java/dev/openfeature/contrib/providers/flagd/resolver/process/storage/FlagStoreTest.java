package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import dev.openfeature.contrib.tools.flagd.core.FlagdCore;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FlagStoreTest {

    // Minimal valid flag configs for testing state transitions
    private static final String VALID_FLAGS_1 =
            "{\"flags\":{\"flag1\":{\"state\":\"ENABLED\",\"variants\":{\"on\":true,\"off\":false},\"defaultVariant\":\"on\"}}}";
    private static final String VALID_FLAGS_2 =
            "{\"flags\":{\"flag1\":{\"state\":\"ENABLED\",\"variants\":{\"on\":true,\"off\":false},\"defaultVariant\":\"on\"},\"flag2\":{\"state\":\"ENABLED\",\"variants\":{\"a\":\"x\"},\"defaultVariant\":\"a\"}}}";
    private static final String INVALID_FLAGS = "not valid json";

    @Test
    void connectorHandling() throws Exception {
        final int maxDelay = 1000;

        final BlockingQueue<QueuePayload> payload = new LinkedBlockingQueue<>();
        FlagStore store = new FlagStore(new MockConnector(payload), new FlagdCore(true));

        store.init();
        final BlockingQueue<StorageStateChange> states = store.getStateQueue();

        // OK for valid flags
        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), () -> {
            payload.offer(new QueuePayload(QueuePayloadType.DATA, VALID_FLAGS_1));
        });

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), () -> {
            assertEquals(StorageState.OK, states.take().getStorageState());
        });

        // STALE for invalid flag
        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), () -> {
            payload.offer(new QueuePayload(QueuePayloadType.DATA, INVALID_FLAGS));
        });

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), () -> {
            assertEquals(StorageState.STALE, states.take().getStorageState());
        });

        // OK again for next valid payload
        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), () -> {
            payload.offer(new QueuePayload(QueuePayloadType.DATA, VALID_FLAGS_2));
        });

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), () -> {
            assertEquals(StorageState.OK, states.take().getStorageState());
        });

        // ERROR is propagated correctly
        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), () -> {
            payload.offer(new QueuePayload(QueuePayloadType.ERROR, null));
        });

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), () -> {
            assertEquals(StorageState.STALE, states.take().getStorageState());
        });

        // Shutdown handling
        store.shutdown();

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), () -> {
            assertEquals(StorageState.STALE, states.take().getStorageState());
        });
    }

    @Test
    public void changedFlags() throws Exception {
        final int maxDelay = 500;
        final BlockingQueue<QueuePayload> payload = new LinkedBlockingQueue<>();
        FlagStore store = new FlagStore(new MockConnector(payload), new FlagdCore(true));
        store.init();
        final BlockingQueue<StorageStateChange> storageStateDTOS = store.getStateQueue();

        // First payload - flag1 is new
        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), () -> {
            payload.offer(new QueuePayload(QueuePayloadType.DATA, VALID_FLAGS_1));
        });
        assertEquals(
                new HashSet<>(Arrays.asList("flag1")),
                new HashSet<>(storageStateDTOS.take().getChangedFlagsKeys()));

        // Second payload - flag2 is new, flag1 unchanged
        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), () -> {
            payload.offer(new QueuePayload(QueuePayloadType.DATA, VALID_FLAGS_2));
        });
        assertEquals(
                new HashSet<>(Arrays.asList("flag2")),
                new HashSet<>(storageStateDTOS.take().getChangedFlagsKeys()));
    }

    @Test
    void shutdownStopsStreamerWhenConnectorEmitsNothing() throws Exception {
        final AtomicReference<Thread> streamer = new AtomicReference<>();
        final CountDownLatch waiting = new CountDownLatch(1);
        final BlockingQueue<QueuePayload> payload = new LinkedBlockingQueue<QueuePayload>() {
            @Override
            public QueuePayload take() throws InterruptedException {
                streamer.set(Thread.currentThread());
                waiting.countDown();
                return super.take();
            }
        };
        // like the gRPC sync connector, this one does not enqueue anything on shutdown
        final QueueSource connector = new QueueSource() {
            @Override
            public void init() {}

            @Override
            public BlockingQueue<QueuePayload> getStreamQueue() {
                return payload;
            }

            @Override
            public void shutdown() {}
        };
        FlagStore store = new FlagStore(connector, new FlagdCore(true));
        store.init();

        assertTrue(waiting.await(1, TimeUnit.SECONDS));
        final Thread thread = streamer.get();
        assertNotNull(thread);
        assertEquals(FlagStore.STREAMER_THREAD_NAME, thread.getName());

        store.shutdown();

        thread.join(1000);
        assertFalse(thread.isAlive());
    }

    @Test
    void initAgainStopsThePreviousStreamer() throws Exception {
        final BlockingQueue<Thread> takers = new LinkedBlockingQueue<>();
        final BlockingQueue<QueuePayload> payload = new LinkedBlockingQueue<QueuePayload>() {
            @Override
            public QueuePayload take() throws InterruptedException {
                takers.add(Thread.currentThread());
                return super.take();
            }
        };
        final QueueSource connector = new QueueSource() {
            @Override
            public void init() {}

            @Override
            public BlockingQueue<QueuePayload> getStreamQueue() {
                return payload;
            }

            @Override
            public void shutdown() {}
        };
        FlagStore store = new FlagStore(connector, new FlagdCore(true));
        store.init();
        final Thread first = takers.poll(1, TimeUnit.SECONDS);
        assertNotNull(first);

        store.init();
        final Thread second = takers.poll(1, TimeUnit.SECONDS);
        assertNotNull(second);

        first.join(1000);
        assertFalse(first.isAlive());

        store.shutdown();
        second.join(1000);
        assertFalse(second.isAlive());
    }
}
