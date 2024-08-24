package dev.openfeature.contrib.providers.flagd.resolver.process.storage;


import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

@Getter
@ToString
@EqualsAndHashCode
public class StorageStateDTO {
    private final StorageState storageState;
    private final List<String> changedFlagsKeys;

    public StorageStateDTO(StorageState storageState, List<String>  changedFlagsKeys) {
        this.storageState = storageState;
        this.changedFlagsKeys = changedFlagsKeys;
    }

    public StorageStateDTO(StorageState storageState) {
        this.storageState = storageState;
        this.changedFlagsKeys = Collections.emptyList();
    }
}
