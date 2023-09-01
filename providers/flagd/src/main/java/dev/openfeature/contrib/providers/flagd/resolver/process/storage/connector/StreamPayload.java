package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class StreamPayload {
    private final StreamPayloadType type;
    private final String data;
}
