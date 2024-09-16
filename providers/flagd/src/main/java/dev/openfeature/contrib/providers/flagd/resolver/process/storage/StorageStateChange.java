package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a change in the stored flags.
 */
@Getter
@ToString
@EqualsAndHashCode
public class StorageStateChange {
    private final StorageState storageState;
    private final List<String> changedFlagsKeys;

    public StorageStateChange(StorageState storageState, List<String> changedFlagsKeys) {
        this.storageState = storageState;
        this.changedFlagsKeys = new ArrayList<>(changedFlagsKeys);
    }

    public StorageStateChange(StorageState storageState) {
        this.storageState = storageState;
        this.changedFlagsKeys = Collections.emptyList();
    }
}
