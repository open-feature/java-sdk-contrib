package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

/**
 * Satus of the storage.
 */
public enum StorageState {
    /**
     * Storage is upto date and working as expected.
     */
    OK,
    /**
     * Storage has gone stale(most recent sync failed). May get to OK status with next sync.
     */
    STALE,
    /**
     * Storage is in an unrecoverable error stage.
     */
    ERROR,
}
