package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

/**
 * File connector reads flag configurations from a given file, polls for changes and expose the content through
 * {@code Connector} contract.
 * The implementation is kept minimal and suites testing, local development needs.
 */
@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "PATH_TRAVERSAL_IN"},
        justification = "File connector read feature flag from a file source.")
@Slf4j
public class FileConnector implements Connector {

    private static final int POLL_INTERVAL_MS = 5000;
    private static final String OFFER_WARN = "Unable to offer file content to queue: queue is full";

    private final String flagSourcePath;
    private final BlockingQueue<QueuePayload> queue = new LinkedBlockingQueue<>(1);
    private boolean shutdown = false;

    public FileConnector(final String flagSourcePath) {
        this.flagSourcePath = flagSourcePath;
    }

    /**
     * Initialize file connector. Reads file content, poll for changes and offer content through the queue.
     */
    public void init() throws IOException {
        Thread watcherT = new Thread(() -> {
            try {
                final Path filePath = Paths.get(flagSourcePath);

                // initial read
                String flagData = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
                if (!queue.offer(new QueuePayload(QueuePayloadType.DATA, flagData, Collections.emptyMap()))) {
                    log.warn(OFFER_WARN);
                }

                long lastTS = Files.getLastModifiedTime(filePath).toMillis();

                // start polling for changes
                while (!shutdown) {
                    long currentTS = Files.getLastModifiedTime(filePath).toMillis();

                    if (currentTS > lastTS) {
                        lastTS = currentTS;
                        flagData = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
                        if (!queue.offer(new QueuePayload(QueuePayloadType.DATA, flagData, Collections.emptyMap()))) {
                            log.warn(OFFER_WARN);
                        }
                    }

                    Thread.sleep(POLL_INTERVAL_MS);
                }

                log.info("Shutting down file connector.");
            } catch (InterruptedException ex) {
                log.error("Interrupted while waiting for polling", ex);
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                log.error("Error from file connector. File connector will exit", t);
                if (!queue.offer(new QueuePayload(QueuePayloadType.ERROR, t.toString(), null))) {
                    log.warn(OFFER_WARN);
                }
            }
        });

        watcherT.setDaemon(true);
        watcherT.start();
        log.info(String.format("Using feature flag configurations from file %s", flagSourcePath));
    }

    /**
     * Expose the queue to fulfil the {@code Connector} contract.
     */
    public BlockingQueue<QueuePayload> getStream() {
        return queue;
    }

    /**
     * Shutdown file connector.
     */
    public void shutdown() throws InterruptedException {
        shutdown = true;
    }
}
