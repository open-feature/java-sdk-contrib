package dev.openfeature.contrib.providers.flagd.resolver.common;

/**
 * Represents the possible states of a connection.
 */
public enum ConnectionState {

    /**
     * The connection is active and functioning as expected.
     */
    CONNECTED,

    /**
     * The connection is not active and has been fully disconnected.
     */
    DISCONNECTED,

    /**
     * The connection is inactive or degraded but may still recover.
     */
    STALE,

    /**
     * The connection has encountered an error and cannot function correctly.
     */
    ERROR,
}