package dev.openfeature.contrib.providers.flagd.resolver.rpc;

import dev.openfeature.flagd.grpc.evaluation.Evaluation.EventStreamResponse;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class EventStreamObserver implements StreamObserver<EventStreamResponse> {
    private final BlockingQueue<EventStreamResponseModel> blockingQueue;

    EventStreamObserver(final BlockingQueue<EventStreamResponseModel> queue) {
        blockingQueue = queue;
    }

    @Override
    public void onNext(EventStreamResponse response) {
        if (!blockingQueue.offer(new EventStreamResponseModel(response))) {
            log.warn("failed to write sync response to queue");
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (!blockingQueue.offer(new EventStreamResponseModel(throwable))) {
            log.warn("failed to write error response to queue");
        }
    }

    @Override
    public void onCompleted() {
        if (!blockingQueue.offer(new EventStreamResponseModel(true))) {
            log.warn("failed to write complete status to queue");
        }
    }
}
