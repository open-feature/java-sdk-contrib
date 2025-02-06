package dev.openfeature.contrib.providers.flagd.resolver.grpc;

import com.google.protobuf.Value;
import dev.openfeature.contrib.providers.flagd.resolver.common.FlagdProviderEvent;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.EventStreamResponse;
import dev.openfeature.sdk.ProviderEvent;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Observer for a gRPC event stream that handles notifications about flag changes and provider readiness events.
 * This class updates a cache and notifies listeners via a lambda callback when events occur.
 */
@Slf4j
@SuppressFBWarnings(justification = "cache needs to be read and write by multiple objects")
class EventStreamObserver implements StreamObserver<EventStreamResponse> {

    private final Consumer<List<String>> onConfigurationChange;
    private final Consumer<FlagdProviderEvent> onReady;

    /**
     * Constructs a new {@code EventStreamObserver} instance.
     *
     * @param onConnectionEvent a consumer to handle connection events with a boolean and a list of changed flags
     */
    EventStreamObserver(Consumer<List<String>> onConfigurationChange, Consumer<FlagdProviderEvent> onReady) {
        this.onConfigurationChange = onConfigurationChange;
        this.onReady = onReady;
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

    @Override
    public void onError(Throwable throwable) {}

    @Override
    public void onCompleted() {}

    /**
     * Handles configuration change events by updating the cache and notifying listeners about changed flags.
     *
     * @param value the event stream response containing configuration change data
     */
    private void handleConfigurationChangeEvent(EventStreamResponse value) {
        log.debug("Received provider change event");
        List<String> changedFlags = new ArrayList<>();

        Map<String, Value> data = value.getData().getFieldsMap();
        Value flagsValue = data.get(Constants.FLAGS_KEY);
        if (flagsValue != null) {
            Map<String, Value> flags = flagsValue.getStructValue().getFieldsMap();
            changedFlags.addAll(flags.keySet());
        }

        onConfigurationChange.accept(changedFlags);
    }

    /**
     * Handles provider readiness events by clearing the cache (if enabled) and notifying listeners of readiness.
     */
    private void handleProviderReadyEvent() {
        log.debug("Received provider ready event");
        onReady.accept(new FlagdProviderEvent(ProviderEvent.PROVIDER_READY));
    }
}
