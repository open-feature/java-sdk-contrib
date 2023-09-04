package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc;

import io.grpc.stub.StreamObserver;
import lombok.extern.java.Log;
import sync.v1.SyncService;

import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;

@Log
class GrpcStreamHandler implements StreamObserver<SyncService.SyncFlagsResponse> {
    private final BlockingQueue<GrpcResponseModel> blockingQueue;

    GrpcStreamHandler(final BlockingQueue<GrpcResponseModel> queue) {
        blockingQueue = queue;
    }

    @Override
    public void onNext(SyncService.SyncFlagsResponse syncFlagsResponse) {
        if (!blockingQueue.offer(new GrpcResponseModel(syncFlagsResponse))) {
            log.log(Level.WARNING, "failed to write sync response to queue");
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (!blockingQueue.offer(new GrpcResponseModel(throwable))) {
            log.log(Level.WARNING, "failed to write error response to queue");
        }
    }

    @Override
    public void onCompleted() {
        if (!blockingQueue.offer(new GrpcResponseModel(true))) {
            log.log(Level.WARNING, "failed to write complete status to queue");
        }
    }
}
