package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.file;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayloadType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;

import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_LONG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.getResourcePath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void throwsErrorIfInvalidFile(){
        // given
        final FileConnector connector = new FileConnector("INVALID_PATH");

        // then
        assertThrows(IOException.class, connector::init);
    }

}
