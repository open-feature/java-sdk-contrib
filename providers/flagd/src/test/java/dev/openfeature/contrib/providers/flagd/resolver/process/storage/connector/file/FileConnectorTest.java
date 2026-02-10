package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileConnectorTest {

    @Test
    void readAndExposeFeatureFlagsFromSource(@TempDir Path tempDir) throws IOException {
        // given - create a temporary file with some content
        Path testFile = tempDir.resolve("flags.txt");
        Files.write(testFile, "test data".getBytes());

        final FileQueueSource connector = new FileQueueSource(testFile.toString(), 5000);

        // when
        connector.init();

        // then
        final BlockingQueue<QueuePayload> stream = connector.getStreamQueue();
        final QueuePayload[] payload = new QueuePayload[1];

        assertNotNull(stream);
        assertTimeoutPreemptively(Duration.ofMillis(200), () -> {
            payload[0] = stream.take();
        });

        assertNotNull(payload[0].getFlagData());
        assertEquals(QueuePayloadType.DATA, payload[0].getType());
    }

    @Test
    void emitErrorStateForInvalidPath() throws IOException {
        // given
        final FileQueueSource connector = new FileQueueSource("INVALID_PATH", 5000);

        // when
        connector.init();

        // then
        final BlockingQueue<QueuePayload> stream = connector.getStreamQueue();

        // Must emit an error within considerable time
        final QueuePayload[] payload = new QueuePayload[1];
        assertTimeoutPreemptively(Duration.ofMillis(200), () -> {
            payload[0] = stream.take();
        });

        assertNotNull(payload[0].getFlagData());
        assertEquals(QueuePayloadType.ERROR, payload[0].getType());
    }

    @Test
    @Disabled("Disabled as unstable on GH Action. Useful for functionality validation")
    void watchForFileUpdatesAndEmitThem(@TempDir Path tempDir) throws IOException {
        final String initial = "initial content";
        final String updated = "updated content";

        // given - create temp file with initial content
        final Path testFile = tempDir.resolve("watchable.txt");
        Files.write(testFile, initial.getBytes());

        final FileQueueSource connector = new FileQueueSource(testFile.toString(), 5000);

        // when
        connector.init();

        // then
        final BlockingQueue<QueuePayload> stream = connector.getStreamQueue();
        final QueuePayload[] payload = new QueuePayload[1];

        // first validate the initial payload
        assertTimeoutPreemptively(Duration.ofMillis(200), () -> {
            payload[0] = stream.take();
        });

        assertEquals(initial, payload[0].getFlagData());

        // then update the flags
        Files.write(testFile, updated.getBytes(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

        // finally wait for updated payload
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            payload[0] = stream.take();
        });

        assertEquals(updated, payload[0].getFlagData());
    }
}
