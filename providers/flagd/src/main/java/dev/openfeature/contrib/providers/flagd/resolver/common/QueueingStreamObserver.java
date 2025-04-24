package dev.openfeature.contrib.providers.flagd.resolver.common;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;

/**
 * Observes gRPC streams for events and enqueues them.
 */
@Slf4j
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "Internal class")
public class QueueingStreamObserver<T> implements StreamObserver<T> {
    private final BlockingQueue<StreamResponseModel<T>> blockingQueue;

    public QueueingStreamObserver(final BlockingQueue<StreamResponseModel<T>> queue) {
        blockingQueue = queue;
        queue.clear();
    }

    @Override
    public void onNext(T response) {
        if (!blockingQueue.offer(new StreamResponseModel<T>(response))) {
            log.warn("failed to write sync response to queue");
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (!blockingQueue.offer(new StreamResponseModel<T>(throwable))) {
            log.warn("failed to write error response to queue");
        }
    }

    @Override
    public void onCompleted() {
        if (!blockingQueue.offer(new StreamResponseModel<T>(true))) {
            log.warn("failed to write complete status to queue");
        }
    }
}
