package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector;

import com.google.protobuf.Struct;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Payload emitted by a {@link QueueSource}. */
@AllArgsConstructor
@Getter
public class QueuePayload {
    public static final QueuePayload ERROR = new QueuePayload(QueuePayloadType.ERROR);
    public static final QueuePayload SHUTDOWN = new QueuePayload(QueuePayloadType.SHUTDOWN);

    private final QueuePayloadType type;
    private final String flagData;
    private final Struct syncContext;

    public QueuePayload(QueuePayloadType type, String flagData) {
        this(type, flagData, null);
    }

    public QueuePayload(QueuePayloadType type) {
        this(type, null, null);
    }
}
