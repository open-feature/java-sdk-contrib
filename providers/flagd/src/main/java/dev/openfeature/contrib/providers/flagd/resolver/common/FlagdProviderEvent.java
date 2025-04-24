package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.Structure;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Represents an event payload for a connection state change in a
 * {@link dev.openfeature.contrib.providers.flagd.resolver.Resolver}.
 * The event includes information about the connection status, any flags that have changed,
 * and metadata associated with the synchronization process.
 */
public class FlagdProviderEvent {

    /**
     * The current state of the connection.
     */
    @Getter
    private final ProviderEvent event;

    /**
     * A list of flags that have changed due to this connection event.
     */
    private final List<String> flagsChanged;

    /**
     * Metadata associated with synchronization in this connection event.
     */
    private final Structure syncMetadata;

    /**
     * Constructs a new {@code ConnectionEvent} with the specified connection state.
     *
     * @param event the event indicating the provider state.
     */
    public FlagdProviderEvent(ProviderEvent event) {
        this(event, Collections.emptyList(), new ImmutableStructure());
    }

    /**
     * Constructs a new {@code ConnectionEvent} with the specified connection state and changed flags.
     *
     * @param event    the event indicating the provider state.
     * @param flagsChanged a list of flags that have changed due to this connection event.
     */
    public FlagdProviderEvent(ProviderEvent event, List<String> flagsChanged) {
        this(event, flagsChanged, new ImmutableStructure());
    }

    /**
     * Constructs a new {@code ConnectionEvent} with the specified connection state and synchronization metadata.
     *
     * @param event   the event indicating the provider state.
     * @param syncMetadata metadata related to the synchronization process of this event.
     */
    public FlagdProviderEvent(ProviderEvent event, Structure syncMetadata) {
        this(event, Collections.emptyList(), new ImmutableStructure(syncMetadata.asMap()));
    }

    /**
     * Constructs a new {@code ConnectionEvent} with the specified connection state, changed flags, and
     * synchronization metadata.
     *
     * @param event the event.
     * @param flagsChanged    a list of flags that have changed due to this connection event.
     * @param syncMetadata    metadata related to the synchronization process of this event.
     */
    public FlagdProviderEvent(ProviderEvent event, List<String> flagsChanged, Structure syncMetadata) {
        this.event = event;
        this.flagsChanged = flagsChanged != null ? flagsChanged : Collections.emptyList(); // Ensure non-null list
        this.syncMetadata = syncMetadata != null
                ? new ImmutableStructure(syncMetadata.asMap())
                : new ImmutableStructure(); // Ensure valid syncMetadata
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

    public boolean isDisconnected() {
        return event == ProviderEvent.PROVIDER_ERROR || event == ProviderEvent.PROVIDER_STALE;
    }
}
