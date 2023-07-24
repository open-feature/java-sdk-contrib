package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.sdk.ProviderState;

/**
 * Defines behaviour required of event stream callbacks.
 */
interface EventStreamCallback {
    void setState(ProviderState state);

    void restartEventStream() throws Exception;

    void emitSuccessReconnectionEvents();

    void emitConfigurationChangeEvent();
}
