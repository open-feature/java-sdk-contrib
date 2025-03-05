package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync;

import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsResponse;
import lombok.Getter;

@Getter
class SyncResponseModel {
    private final SyncFlagsResponse syncFlagsResponse;
    private final Throwable error;
    private final boolean complete;

    public SyncResponseModel(final Throwable error) {
        this(null, error, false);
    }

    public SyncResponseModel(final SyncFlagsResponse syncFlagsResponse) {
        this(syncFlagsResponse, null, false);
    }

    public SyncResponseModel(final Boolean complete) {
        this(null, null, complete);
    }

    SyncResponseModel(SyncFlagsResponse syncFlagsResponse, Throwable error, boolean complete) {
        this.syncFlagsResponse = syncFlagsResponse;
        this.error = error;
        this.complete = complete;
    }
}
