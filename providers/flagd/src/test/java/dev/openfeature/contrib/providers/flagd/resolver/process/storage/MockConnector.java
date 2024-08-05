package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayloadType;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;

@Slf4j
public class MockConnector implements Connector {

    private BlockingQueue<StreamPayload> mockQueue;

    public MockConnector(final BlockingQueue<StreamPayload> mockQueue) {
        this.mockQueue = mockQueue;
    }

    public void init() {
        // no-op
    }

    public BlockingQueue<StreamPayload> getStream() {
        return mockQueue;
    }

    public void shutdown() {
        // Emit error mocking closed connection scenario
        if (!mockQueue.offer(new StreamPayload(StreamPayloadType.ERROR, "shutdown invoked"))) {
            log.warn("Failed to offer shutdown status");
        }
    }
}
