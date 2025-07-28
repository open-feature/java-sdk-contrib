package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MockConnector implements QueueSource {

    private BlockingQueue<QueuePayload> mockQueue;

    public MockConnector(final BlockingQueue<QueuePayload> mockQueue) {
        this.mockQueue = mockQueue;
    }

    public void init() {
        // no-op
    }

    public BlockingQueue<QueuePayload> getStreamQueue() {
        return mockQueue;
    }

    public void shutdown() {
        // Emit error mocking closed connection scenario
        if (!mockQueue.offer(new QueuePayload(QueuePayloadType.ERROR, "shutdown invoked"))) {
            log.warn("Failed to offer shutdown status");
        }
    }
}
