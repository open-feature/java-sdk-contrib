package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc;

import lombok.Getter;
import sync.v1.SyncService;

@Getter
class GrpcResponseModel {
    private final SyncService.SyncFlagsResponse syncFlagsResponse;
    private final Throwable error;
    private final boolean complete;

    public GrpcResponseModel(final Throwable error) {
        this(null, error, false);
    }

    public GrpcResponseModel(final SyncService.SyncFlagsResponse syncFlagsResponse) {
        this(syncFlagsResponse, null, false);
    }

    public GrpcResponseModel(final Boolean complete) {
        this(null, null, complete);
    }

    GrpcResponseModel(SyncService.SyncFlagsResponse syncFlagsResponse, Throwable error, boolean complete) {
        this.syncFlagsResponse = syncFlagsResponse;
        this.error = error;
        this.complete = complete;
    }
}
