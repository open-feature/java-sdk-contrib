package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.Structure;

import java.util.Collections;
import java.util.List;

/**
 * Represents an event payload for a connection state change in a
 * {@link dev.openfeature.contrib.providers.flagd.resolver.Resolver}.
 * The event includes information about the connection status, any flags that have changed,
 * and metadata associated with the synchronization process.
 */
public class ConnectionEvent {

    /**
     * The current state of the connection.
     */
    private final ConnectionState connected;

    /**
     * A list of flags that have changed due to this connection event.
     */
    private final List<String> flagsChanged;

    /**
     * Metadata associated with synchronization in this connection event.
     */
    private final Structure syncMetadata;

    /**
     * Constructs a new {@code ConnectionEvent} with the connection status only.
     *
     * @param connected {@code true} if the connection is established, otherwise {@code false}.
     */
    public ConnectionEvent(boolean connected) {
        this(connected ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED, Collections.emptyList(),
                new ImmutableStructure());
    }

    /**
     * Constructs a new {@code ConnectionEvent} with the specified connection state.
     *
     * @param connected the connection state indicating if the connection is established or not.
     */
    public ConnectionEvent(ConnectionState connected) {
        this(connected, Collections.emptyList(), new ImmutableStructure());
    }

    /**
     * Constructs a new {@code ConnectionEvent} with the specified connection state and changed flags.
     *
     * @param connected    the connection state indicating if the connection is established or not.
     * @param flagsChanged a list of flags that have changed due to this connection event.
     */
    public ConnectionEvent(ConnectionState connected, List<String> flagsChanged) {
        this(connected, flagsChanged, new ImmutableStructure());
    }

    /**
     * Constructs a new {@code ConnectionEvent} with the specified connection state and synchronization metadata.
     *
     * @param connected    the connection state indicating if the connection is established or not.
     * @param syncMetadata metadata related to the synchronization process of this event.
     */
    public ConnectionEvent(ConnectionState connected, Structure syncMetadata) {
        this(connected, Collections.emptyList(), new ImmutableStructure(syncMetadata.asMap()));
    }

    /**
     * Constructs a new {@code ConnectionEvent} with the specified connection state, changed flags, and
     * synchronization metadata.
     *
     * @param connectionState the state of the connection.
     * @param flagsChanged    a list of flags that have changed due to this connection event.
     * @param syncMetadata    metadata related to the synchronization process of this event.
     */
    public ConnectionEvent(ConnectionState connectionState, List<String> flagsChanged, Structure syncMetadata) {
        this.connected = connectionState;
        this.flagsChanged = flagsChanged != null ? flagsChanged : Collections.emptyList();  // Ensure non-null list
        this.syncMetadata = syncMetadata != null ? new ImmutableStructure(syncMetadata.asMap()) :
                new ImmutableStructure(); // Ensure valid syncMetadata
    }

    /**
     * Retrieves an unmodifiable view of the list of changed flags.
     *
     * @return an unmodifiable list of changed flags.
     */
    public List<String> getFlagsChanged() {
        return Collections.unmodifiableList(flagsChanged);
    }

    /**
     * Retrieves the synchronization metadata represented as an immutable SDK structure type.
     *
     * @return an immutable structure containing the synchronization metadata.
     */
    public Structure getSyncMetadata() {
        return new ImmutableStructure(syncMetadata.asMap());
    }

    /**
     * Indicates whether the current connection state is connected.
     *
     * @return {@code true} if connected, otherwise {@code false}.
     */
    public boolean isConnected() {
        return this.connected == ConnectionState.CONNECTED;
    }

    /**
     * Indicates
     * whether the current connection state is stale.
     *
     * @return {@code true} if stale, otherwise {@code false}.
     */
    public boolean isStale() {
        return this.connected == ConnectionState.STALE;
    }
}
