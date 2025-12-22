package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector;

/** Payload type emitted by {@link QueueSource}. */
public enum QueuePayloadType {
    DATA,
    ERROR,
    SHUTDOWN
}
