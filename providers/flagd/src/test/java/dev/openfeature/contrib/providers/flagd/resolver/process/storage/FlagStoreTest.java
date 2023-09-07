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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class FlagStoreTest {

    @Test
    public void connectorHandling() {
        final int maxDelay = 500;

        final BlockingQueue<StreamPayload> payload = new LinkedBlockingQueue<>();
        FlagStore store = new FlagStore(new MockConnector(payload));

        store.init();
        final BlockingQueue<StorageState> states = store.getStateQueue();

        // OK for simple flag
        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            payload.offer(new StreamPayload(StreamPayloadType.DATA, getFlagsFromResource(VALID_SIMPLE)));
        });

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            assertEquals(StorageState.OK,  states.take());
        });

        // STALE for invalid flag
        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            payload.offer(new StreamPayload(StreamPayloadType.DATA, getFlagsFromResource(INVALID_FLAG)));
        });

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            assertEquals(StorageState.STALE,  states.take());
        });

        // OK again for next payload
        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            payload.offer(new StreamPayload(StreamPayloadType.DATA, getFlagsFromResource(VALID_LONG)));
        });

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            assertEquals(StorageState.OK,  states.take());
        });

        // ERROR is propagated correctly
        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            payload.offer(new StreamPayload(StreamPayloadType.ERROR, null));
        });

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            assertEquals(StorageState.ERROR,  states.take());
        });

        // Shutdown handling
        store.shutdown();

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            assertEquals(StorageState.ERROR,  states.take());
        });
    }

}
