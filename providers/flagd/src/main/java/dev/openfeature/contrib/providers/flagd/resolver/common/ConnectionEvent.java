package dev.openfeature.contrib.providers.flagd.resolver.common;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Event payload for a
 * {@link dev.openfeature.contrib.providers.flagd.resolver.Resolver} connection
 * state change event.
 */
@AllArgsConstructor
public class ConnectionEvent {
    @Getter
    private final boolean connected;
    private final List<String> flagsChanged;
    private final Map<String, Object> syncMetadata;

    /**
     * Construct a new ConnectionEvent.
     * 
     * @param connected status of the connection
     */
    public ConnectionEvent(boolean connected) {
        this(connected, Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * Construct a new ConnectionEvent.
     * 
     * @param connected    status of the connection
     * @param flagsChanged list of flags changed
     */
    public ConnectionEvent(boolean connected, List<String> flagsChanged) {
        this(connected, flagsChanged, Collections.emptyMap());
    }

    /**
     * Construct a new ConnectionEvent.
     * 
     * @param connected    status of the connection
     * @param syncMetadata sync.getMetadata
     */
    public ConnectionEvent(boolean connected, Map<String, Object> syncMetadata) {
        this(connected, Collections.emptyList(), syncMetadata);
    }

    /**
     * Get changed flags.
     * 
     * @return an unmodifiable view of the changed flags
     */
    public List<String> getFlagsChanged() {
        return Collections.unmodifiableList(flagsChanged);
    }

    /**
     * Get changed sync metadata.
     * 
     * @return an unmodifiable view of the sync metadata
     */
    public Map<String, Object> getSyncMetadata() {
        return Collections.unmodifiableMap(syncMetadata);
    }
}
