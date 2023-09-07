package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Payload emitted by a {@link Connector}.
 */
@AllArgsConstructor
@Getter
public class StreamPayload {
    private final StreamPayloadType type;
    private final String data;
}
