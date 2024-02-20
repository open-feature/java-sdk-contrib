package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc;

import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsResponse;
import lombok.Getter;

@Getter
class GrpcResponseModel {
    private final SyncFlagsResponse syncFlagsResponse;
    private final Throwable error;
    private final boolean complete;

    public GrpcResponseModel(final Throwable error) {
        this(null, error, false);
    }

    public GrpcResponseModel(final SyncFlagsResponse syncFlagsResponse) {
        this(syncFlagsResponse, null, false);
    }

    public GrpcResponseModel(final Boolean complete) {
        this(null, null, complete);
    }

    GrpcResponseModel(SyncFlagsResponse syncFlagsResponse, Throwable error, boolean complete) {
        this.syncFlagsResponse = syncFlagsResponse;
        this.error = error;
        this.complete = complete;
    }
}
