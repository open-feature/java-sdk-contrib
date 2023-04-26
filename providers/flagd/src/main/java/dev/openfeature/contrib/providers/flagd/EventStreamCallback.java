package dev.openfeature.contrib.providers.flagd;

/**
 * Defines behaviour required of event stream callbacks.
 */
interface EventStreamCallback {
    void setEventStreamAlive(boolean alive);

    void restartEventStream() throws Exception;
}
