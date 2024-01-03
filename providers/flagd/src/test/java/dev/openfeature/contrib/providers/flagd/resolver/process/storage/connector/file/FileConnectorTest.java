package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.file;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayloadType;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;

import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_LONG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.getResourcePath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class FileConnectorTest {

    @Test
    void readAndExposeFeatureFlagsFromSource() throws IOException {
        // given
        final FileConnector connector = new FileConnector(getResourcePath(VALID_LONG));

        // when
        connector.init();

        // then
        final BlockingQueue<StreamPayload> stream = connector.getStream();
        final StreamPayload[] payload = new StreamPayload[1];

        assertNotNull(stream);
        assertTimeoutPreemptively(Duration.ofMillis(200), () -> {
            payload[0] = stream.take();
        });

        assertNotNull(payload[0].getData());
        assertEquals(StreamPayloadType.DATA, payload[0].getType());
    }

    @Test
    void emitErrorStateForInvalidPath() throws IOException {
        // given
        final FileConnector connector = new FileConnector("INVALID_PATH");

        // when
        connector.init();

        // then
        final BlockingQueue<StreamPayload> stream = connector.getStream();

        // Must emit an error within considerable time
        final StreamPayload[] payload = new StreamPayload[1];
        assertTimeoutPreemptively(Duration.ofMillis(200), () -> {
            payload[0] = stream.take();
        });

        assertNotNull(payload[0].getData());
        assertEquals(StreamPayloadType.ERROR, payload[0].getType());
    }

    @Test
    void watchForFileUpdatesAndEmitThem() throws IOException {
        final String initial = "{\"flags\":{\"myBoolFlag\":{\"state\":\"ENABLED\",\"variants\":{\"on\":true," +
                "\"off\":false},\"defaultVariant\":\"on\"}}}";

        final String updatedFlags = "{\"flags\":{\"myBoolFlag\":{\"state\":\"ENABLED\",\"variants\":{\"on\":true," +
                "\"off\":false},\"defaultVariant\":\"off\"}}}";

        // given
        final File tmpFlagFile = File.createTempFile("flagd", "flags");
        final Path flagSourcePath = tmpFlagFile.toPath();

        Files.write(flagSourcePath, initial.getBytes(), StandardOpenOption.WRITE);
        final FileConnector connector = new FileConnector(flagSourcePath.toString());

        // when
        connector.init();

        // then
        final BlockingQueue<StreamPayload> stream = connector.getStream();

        final StreamPayload[] payload = new StreamPayload[1];

        // first validate the initial payload
        assertTimeoutPreemptively(Duration.ofMillis(200), () -> {
            payload[0] = stream.take();
        });

        assertEquals(initial, payload[0].getData());

        // then update the flags
        Files.write(flagSourcePath, updatedFlags.getBytes(), StandardOpenOption.WRITE);

        // finally wait for updated payload
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            payload[0] = stream.take();
        });

        assertEquals(updatedFlags, payload[0].getData());
    }

}
