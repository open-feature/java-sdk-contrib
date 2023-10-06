package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.file;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayloadType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * File connector reads flag configurations and expose the context through {@code Connector} contract.
 * The implementation is kept minimal and suites testing, local development needs.
 */
@Slf4j
public class FileConnector implements Connector {

    private final String flagSourcePath;
    private final BlockingQueue<StreamPayload> queue = new LinkedBlockingQueue<>(1);

    public FileConnector(final String flagSourcePath) {
        this.flagSourcePath = flagSourcePath;
    }

    /**
     * Initialize file connector. Reads content of the provided source file and offer it through queue.
     */
    public void init() throws IOException {
        final String flagData = new String(Files.readAllBytes(Paths.get(flagSourcePath)));

        if (!queue.offer(new StreamPayload(StreamPayloadType.DATA, flagData))) {
            throw new RuntimeException("Unable to write to queue. Que is full.");
        }

        log.info(String.format("Using feature flag configurations from file %s", flagSourcePath));
    }

    /**
     * Expose the queue to fulfil the {@code Connector} contract.
     */
    public BlockingQueue<StreamPayload> getStream() {
        return queue;
    }

    /**
     * NO-OP shutdown.
     */
    public void shutdown() throws InterruptedException {
        // NO-OP nothing to do here
    }
}
