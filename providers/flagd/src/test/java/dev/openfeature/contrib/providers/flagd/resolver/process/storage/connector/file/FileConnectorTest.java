package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.file;

import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.UPDATABLE_FILE;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_LONG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.getResourcePath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class FileConnectorTest {

    @Test
    void readAndExposeFeatureFlagsFromSource() throws IOException {
        // given
        final FileConnector connector = new FileConnector(getResourcePath(VALID_LONG));

        // when
        connector.init();

        // then
        final BlockingQueue<QueuePayload> stream = connector.getStream();
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
        final FileConnector connector = new FileConnector("INVALID_PATH");

        // when
        connector.init();

        // then
        final BlockingQueue<QueuePayload> stream = connector.getStream();

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
    void watchForFileUpdatesAndEmitThem() throws IOException {
        final String initial =
                "{\"flags\":{\"myBoolFlag\":{\"state\":\"ENABLED\",\"variants\":{\"on\":true,\"off\":false},\"defaultVariant\":\"on\"}}}";
        final String updatedFlags =
                "{\"flags\":{\"myBoolFlag\":{\"state\":\"ENABLED\",\"variants\":{\"on\":true,\"off\":false},\"defaultVariant\":\"off\"}}}";

        // given
        final Path updPath = Paths.get(getResourcePath(UPDATABLE_FILE));
        Files.write(updPath, initial.getBytes(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

        final FileConnector connector = new FileConnector(updPath.toString());

        // when
        connector.init();

        // then
        final BlockingQueue<QueuePayload> stream = connector.getStream();
        final QueuePayload[] payload = new QueuePayload[1];

        // first validate the initial payload
        assertTimeoutPreemptively(Duration.ofMillis(200), () -> {
            payload[0] = stream.take();
        });

        assertEquals(initial, payload[0].getFlagData());

        // then update the flags
        Files.write(updPath, updatedFlags.getBytes(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

        // finally wait for updated payload
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            payload[0] = stream.take();
        });

        assertEquals(updatedFlags, payload[0].getFlagData());
    }
}
