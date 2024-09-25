package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Payload emitted by a {@link Connector}.
 */
@AllArgsConstructor
@Getter
public class QueuePayload {
    private final QueuePayloadType type;
    private final String flagData;
    private final Map<String, Object> syncMetadata;
}
