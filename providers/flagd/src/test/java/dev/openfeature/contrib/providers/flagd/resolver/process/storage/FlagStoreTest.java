package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FlagParser;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayloadType;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.INVALID_FLAG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_LONG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_SIMPLE;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.getFlagsFromResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class FlagStoreTest {

    @Test
    public void connectorHandling() throws Exception {
        final int maxDelay = 1000;

        final BlockingQueue<StreamPayload> payload = new LinkedBlockingQueue<>();
        FlagStore store = new FlagStore(new MockConnector(payload), true);

        store.init();
        final BlockingQueue<StorageStateChange> states = store.getStateQueue();

        // OK for simple flag
        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            payload.offer(new StreamPayload(StreamPayloadType.DATA, getFlagsFromResource(VALID_SIMPLE)));
        });

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            assertEquals(StorageState.OK,  states.take().getStorageState());
        });

        // STALE for invalid flag
        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            payload.offer(new StreamPayload(StreamPayloadType.DATA, getFlagsFromResource(INVALID_FLAG)));
        });

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            assertEquals(StorageState.STALE,  states.take().getStorageState());
        });

        // OK again for next payload
        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            payload.offer(new StreamPayload(StreamPayloadType.DATA, getFlagsFromResource(VALID_LONG)));
        });

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            assertEquals(StorageState.OK,  states.take().getStorageState());
        });

        // ERROR is propagated correctly
        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            payload.offer(new StreamPayload(StreamPayloadType.ERROR, null));
        });

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            assertEquals(StorageState.ERROR,  states.take().getStorageState());
        });

        // Shutdown handling
        store.shutdown();

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            assertEquals(StorageState.ERROR,  states.take().getStorageState());
        });
    }

    @Test
    public void changedFlags() throws Exception {
        final int maxDelay = 500;
        final BlockingQueue<StreamPayload> payload = new LinkedBlockingQueue<>();
        FlagStore store = new FlagStore(new MockConnector(payload), true);
        store.init();
        final BlockingQueue<StorageStateChange> storageStateDTOS = store.getStateQueue();

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            payload.offer(new StreamPayload(StreamPayloadType.DATA, getFlagsFromResource(VALID_SIMPLE)));
        });
        // flags changed for first time
        assertEquals(FlagParser.parseString(
                getFlagsFromResource(VALID_SIMPLE), true).keySet().stream().collect(Collectors.toList()),
                storageStateDTOS.take().getChangedFlagsKeys());

        assertTimeoutPreemptively(Duration.ofMillis(maxDelay), ()-> {
            payload.offer(new StreamPayload(StreamPayloadType.DATA, getFlagsFromResource(VALID_LONG)));
        });
        Map<String, FeatureFlag> expectedChangedFlags =
                FlagParser.parseString(getFlagsFromResource(VALID_LONG),true);
        expectedChangedFlags.remove("myBoolFlag");
        // flags changed from initial VALID_SIMPLE flag, as a set because we don't care about order
        Assert.assertEquals(expectedChangedFlags.keySet().stream().collect(Collectors.toSet()),
        new HashSet<>(storageStateDTOS.take().getChangedFlagsKeys()));
    }

}
