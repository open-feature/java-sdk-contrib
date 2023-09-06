package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayloadType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.INVALID_FLAG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_LONG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_SIMPLE;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.getFlagsFromResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class FlagStoreTest {

    @Test
    public void connectorHandling() {
        final BlockingQueue<StreamPayload> payload = new LinkedBlockingQueue<>();
        FlagStore store = new FlagStore(new MockConnector(payload));

        store.init();
        final BlockingQueue<StorageState> states = store.getStateQueue();

        // OK for simple flag
        assertTimeout(Duration.ofMillis(200), ()-> {
            payload.offer(new StreamPayload(StreamPayloadType.DATA, getFlagsFromResource(VALID_SIMPLE)));
        });

        assertTimeout(Duration.ofMillis(200), ()-> {
            assertEquals(StorageState.OK,  states.take());
        });

        // STALE for invalid flag
        assertTimeout(Duration.ofMillis(200), ()-> {
            payload.offer(new StreamPayload(StreamPayloadType.DATA, getFlagsFromResource(INVALID_FLAG)));
        });

        assertTimeout(Duration.ofMillis(200), ()-> {
            assertEquals(StorageState.STALE,  states.take());
        });

        // OK again for next payload
        assertTimeout(Duration.ofMillis(200), ()-> {
            payload.offer(new StreamPayload(StreamPayloadType.DATA, getFlagsFromResource(VALID_LONG)));
        });

        assertTimeout(Duration.ofMillis(200), ()-> {
            assertEquals(StorageState.OK,  states.take());
        });

        // ERROR is propagated correctly
        assertTimeout(Duration.ofMillis(200), ()-> {
            payload.offer(new StreamPayload(StreamPayloadType.ERROR, null));
        });

        assertTimeout(Duration.ofMillis(200), ()-> {
            assertEquals(StorageState.ERROR,  states.take());
        });

        // Shutdown handling
        store.shutdown();

        assertTimeout(Duration.ofMillis(200), ()-> {
            assertEquals(StorageState.ERROR,  states.take());
        });
    }

}