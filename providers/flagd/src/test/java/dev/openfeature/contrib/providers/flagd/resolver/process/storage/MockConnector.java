package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.Collections;

@Slf4j
public class MockConnector implements Connector {

    private BlockingQueue<QueuePayload> mockQueue;

    public MockConnector(final BlockingQueue<QueuePayload> mockQueue) {
        this.mockQueue = mockQueue;
    }

    public void init() {
        // no-op
    }

    public BlockingQueue<QueuePayload> getStream() {
        return mockQueue;
    }

    public void shutdown() {
        // Emit error mocking closed connection scenario
        if (!mockQueue.offer(new QueuePayload(QueuePayloadType.ERROR, "shutdown invoked", Collections.emptyMap()))) {
            log.warn("Failed to offer shutdown status");
        }
    }
}
