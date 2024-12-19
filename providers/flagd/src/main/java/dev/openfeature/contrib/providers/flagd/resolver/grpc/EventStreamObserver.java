package dev.openfeature.contrib.providers.flagd.resolver.grpc;

import com.google.protobuf.Value;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.Cache;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.EventStreamResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Observer for a gRPC event stream that handles notifications about flag changes and provider readiness events.
 * This class updates a cache and notifies listeners via a lambda callback when events occur.
 */
@Slf4j
@SuppressFBWarnings(justification = "cache needs to be read and write by multiple objects")
class EventStreamObserver implements StreamObserver<EventStreamResponse> {

    /**
     * A consumer to handle connection events with a flag indicating success and a list of changed flags.
     */
    private final BiConsumer<Boolean, List<String>> onConnectionEvent;

    /**
     * The cache to update based on received events.
     */
    private final Cache cache;

    /**
     * Constructs a new {@code EventStreamObserver} instance.
     *
     * @param cache             the cache to update based on received events
     * @param onConnectionEvent a consumer to handle connection events with a boolean and a list of changed flags
     */
    EventStreamObserver(Cache cache, BiConsumer<Boolean, List<String>> onConnectionEvent) {
        this.cache = cache;
        this.onConnectionEvent = onConnectionEvent;
    }

    /**
     * Called when a new event is received from the stream.
     *
     * @param value the event stream response containing event data
     */
    @Override
    public void onNext(EventStreamResponse value) {
        switch (value.getType()) {
            case Constants.CONFIGURATION_CHANGE:
                this.handleConfigurationChangeEvent(value);
                break;
            case Constants.PROVIDER_READY:
                this.handleProviderReadyEvent();
                break;
            default:
                log.debug("Unhandled event type {}", value.getType());
        }
    }

    /**
     * Called when an error occurs in the stream.
     *
     * @param throwable the error that occurred
     */
    @Override
    public void onError(Throwable throwable) {
        if (this.cache.getEnabled().equals(Boolean.TRUE)) {
            this.cache.clear();
        }
    }

    /**
     * Called when the stream is completed.
     */
    @Override
    public void onCompleted() {
        if (this.cache.getEnabled().equals(Boolean.TRUE)) {
            this.cache.clear();
        }
        this.onConnectionEvent.accept(false, Collections.emptyList());
    }

    /**
     * Handles configuration change events by updating the cache and notifying listeners about changed flags.
     *
     * @param value the event stream response containing configuration change data
     */
    private void handleConfigurationChangeEvent(EventStreamResponse value) {
        List<String> changedFlags = new ArrayList<>();
        boolean cachingEnabled = this.cache.getEnabled();

        Map<String, Value> data = value.getData().getFieldsMap();
        Value flagsValue = data.get(Constants.FLAGS_KEY);
        if (flagsValue == null) {
            if (cachingEnabled) {
                this.cache.clear();
            }
        } else {
            Map<String, Value> flags = flagsValue.getStructValue().getFieldsMap();
            for (String flagKey : flags.keySet()) {
                changedFlags.add(flagKey);
                if (cachingEnabled) {
                    this.cache.remove(flagKey);
                }
            }
        }

        this.onConnectionEvent.accept(true, changedFlags);
    }

    /**
     * Handles provider readiness events by clearing the cache (if enabled) and notifying listeners of readiness.
     */
    private void handleProviderReadyEvent() {
        this.onConnectionEvent.accept(true, Collections.emptyList());
        if (this.cache.getEnabled().equals(Boolean.TRUE)) {
            this.cache.clear();
        }
    }
}
