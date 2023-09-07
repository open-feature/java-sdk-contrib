package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayloadType;
import lombok.extern.java.Log;

import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;

@Log
public class MockConnector implements Connector {

    private BlockingQueue<StreamPayload> mockQueue;

    MockConnector(final BlockingQueue<StreamPayload> mockQueue){
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
        if(!mockQueue.offer(new StreamPayload(StreamPayloadType.ERROR, "shutdown invoked"))){
            log.log(Level.WARNING, "Failed to offer shutdown status");
        }
    }
}
