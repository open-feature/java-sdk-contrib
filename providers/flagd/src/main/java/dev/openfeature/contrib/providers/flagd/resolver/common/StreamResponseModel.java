package dev.openfeature.contrib.providers.flagd.resolver.common;

import lombok.Getter;

/**
 * Generic container for stream responses.
 */
@Getter
public class StreamResponseModel<T> {
    private final T response;
    private final Throwable error;
    private final boolean complete;

    public StreamResponseModel(final Throwable error) {
        this(null, error, false);
    }

    public StreamResponseModel(final T response) {
        this(response, null, false);
    }

    public StreamResponseModel(final Boolean complete) {
        this(null, null, complete);
    }

    StreamResponseModel(T response, Throwable error, boolean complete) {
        this.response = response;
        this.error = error;
        this.complete = complete;
    }
}
