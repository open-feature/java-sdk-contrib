package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

/** Status of the storage. */
public enum StorageState {
    /** Storage is upto date and working as expected. */
    OK,
    /** Storage has gone stale (most recent sync failed). May get to OK status with next sync. */
    TRANSIENT_ERROR,
    /** Storage is in an unrecoverable error stage. */
    FATAL_ERROR,
}
