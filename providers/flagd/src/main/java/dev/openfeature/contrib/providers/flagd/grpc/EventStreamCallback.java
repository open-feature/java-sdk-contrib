package dev.openfeature.contrib.providers.flagd.grpc;

import dev.openfeature.sdk.ProviderState;

/**
 * Defines behaviour required of event stream callbacks.
 */
public interface EventStreamCallback {
    void setState(ProviderState state);

    void restartEventStream() throws Exception;

    void emitSuccessReconnectionEvents();

    void emitConfigurationChangeEvent();
}
