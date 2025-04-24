package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector;

import java.util.concurrent.BlockingQueue;

/**
 * Contract of the in-process storage queue source, responsible enqueueing and dequeuing
 * change messages.
 */
public interface QueueSource {
    void init() throws Exception;

    BlockingQueue<QueuePayload> getStreamQueue();

    void shutdown() throws InterruptedException;
}
