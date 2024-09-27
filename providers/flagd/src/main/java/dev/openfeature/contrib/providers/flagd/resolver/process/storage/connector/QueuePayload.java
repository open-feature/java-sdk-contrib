package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector;

import dev.openfeature.flagd.grpc.sync.Sync.GetMetadataResponse;
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
    private final GetMetadataResponse metadataResponse;

    public QueuePayload(QueuePayloadType type, String flagData) {
        this(type, flagData, GetMetadataResponse.getDefaultInstance());
    }
}
