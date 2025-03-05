package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync;

import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsResponse;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class SyncStreamObserver implements StreamObserver<SyncFlagsResponse> {
    private final BlockingQueue<SyncResponseModel> blockingQueue;

    SyncStreamObserver(final BlockingQueue<SyncResponseModel> queue) {
        blockingQueue = queue;
    }

    @Override
    public void onNext(SyncFlagsResponse syncFlagsResponse) {
        if (!blockingQueue.offer(new SyncResponseModel(syncFlagsResponse))) {
            log.warn("failed to write sync response to queue");
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (!blockingQueue.offer(new SyncResponseModel(throwable))) {
            log.warn("failed to write error response to queue");
        }
    }

    @Override
    public void onCompleted() {
        if (!blockingQueue.offer(new SyncResponseModel(true))) {
            log.warn("failed to write complete status to queue");
        }
    }
}
