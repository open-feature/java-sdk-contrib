package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import java.util.Collections;
import java.util.List;

import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.Structure;
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
    private final Structure syncMetadata;

    /**
     * Construct a new StorageStateChange.
     * @param storageState state of the storage
     * @param changedFlagsKeys flags changed
     * @param syncMetadata possibly updated metadata 
     */
    public StorageStateChange(StorageState storageState, List<String> changedFlagsKeys,
            Structure syncMetadata) {
        this.storageState = storageState;
        this.changedFlagsKeys = Collections.unmodifiableList(changedFlagsKeys);
        this.syncMetadata = new ImmutableStructure(syncMetadata.asMap());
    }

    /**
     * Construct a new StorageStateChange.
     * @param storageState state of the storage
     * @param changedFlagsKeys flags changed
     */
    public StorageStateChange(StorageState storageState, List<String> changedFlagsKeys) {
        this.storageState = storageState;
        this.changedFlagsKeys = Collections.unmodifiableList(changedFlagsKeys);
        this.syncMetadata = new ImmutableStructure();
    }

    /**
     * Construct a new StorageStateChange.
     * @param storageState state of the storage
     */
    public StorageStateChange(StorageState storageState) {
        this.storageState = storageState;
        this.changedFlagsKeys = Collections.emptyList();
        this.syncMetadata = new ImmutableStructure();
    }
}
