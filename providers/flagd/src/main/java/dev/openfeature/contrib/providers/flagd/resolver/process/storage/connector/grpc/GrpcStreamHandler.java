package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc;

import dev.openfeature.flagd.sync.SyncService;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;

@Slf4j
class GrpcStreamHandler implements StreamObserver<SyncService.SyncFlagsResponse> {
    private final BlockingQueue<GrpcResponseModel> blockingQueue;

    GrpcStreamHandler(final BlockingQueue<GrpcResponseModel> queue) {
        blockingQueue = queue;
    }

    @Override
    public void onNext(SyncService.SyncFlagsResponse syncFlagsResponse) {
        if (!blockingQueue.offer(new GrpcResponseModel(syncFlagsResponse))) {
            log.warn("failed to write sync response to queue");
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (!blockingQueue.offer(new GrpcResponseModel(throwable))) {
            log.warn("failed to write error response to queue");
        }
    }

    @Override
    public void onCompleted() {
        if (!blockingQueue.offer(new GrpcResponseModel(true))) {
            log.warn("failed to write complete status to queue");
        }
    }
}
