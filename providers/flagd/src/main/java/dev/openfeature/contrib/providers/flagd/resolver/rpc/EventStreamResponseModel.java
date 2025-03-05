package dev.openfeature.contrib.providers.flagd.resolver.rpc;

import dev.openfeature.flagd.grpc.evaluation.Evaluation.EventStreamResponse;
import lombok.Getter;

@Getter
class EventStreamResponseModel {
    private final EventStreamResponse response;
    private final Throwable error;
    private final boolean complete;

    public EventStreamResponseModel(final Throwable error) {
        this(null, error, false);
    }

    public EventStreamResponseModel(final EventStreamResponse syncFlagsResponse) {
        this(syncFlagsResponse, null, false);
    }

    public EventStreamResponseModel(final Boolean complete) {
        this(null, null, complete);
    }

    EventStreamResponseModel(EventStreamResponse response, Throwable error, boolean complete) {
        this.response = response;
        this.error = error;
        this.complete = complete;
    }
}
