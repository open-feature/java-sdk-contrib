package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents a change in the stored flags.
 */
@Getter
@ToString
@EqualsAndHashCode
public class StorageStateChange {
    private final StorageState storageState;
    private final List<String> changedFlagsKeys;
    private final Map<String, Object> syncMetadata;

    /**
     * Construct a new StorageStateChange.
     * @param storageState state of the storage
     * @param changedFlagsKeys flags changed
     * @param syncMetadata possibly updated metadata 
     */
    public StorageStateChange(StorageState storageState, List<String> changedFlagsKeys,
            Map<String, Object> syncMetadata) {
        this.storageState = storageState;
        this.changedFlagsKeys = Collections.unmodifiableList(changedFlagsKeys);
        this.syncMetadata = Collections.unmodifiableMap(syncMetadata);
    }

    /**
     * Construct a new StorageStateChange.
     * @param storageState state of the storage
     * @param changedFlagsKeys flags changed
     */
    public StorageStateChange(StorageState storageState, List<String> changedFlagsKeys) {
        this.storageState = storageState;
        this.changedFlagsKeys = Collections.unmodifiableList(changedFlagsKeys);
        this.syncMetadata = Collections.emptyMap();
    }

    /**
     * Construct a new StorageStateChange.
     * @param storageState state of the storage
     */
    public StorageStateChange(StorageState storageState) {
        this.storageState = storageState;
        this.changedFlagsKeys = Collections.emptyList();
        this.syncMetadata = Collections.emptyMap();
    }
}
