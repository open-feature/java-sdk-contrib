package dev.openfeature.contrib.providers.flagd.grpc;

import com.google.protobuf.Value;
import dev.openfeature.contrib.providers.flagd.cache.Cache;
import dev.openfeature.flagd.grpc.Schema.EventStreamResponse;
import dev.openfeature.sdk.ProviderState;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Consumer;

/**
 * EventStreamObserver handles events emitted by flagd.
 */
@Slf4j
@SuppressFBWarnings(justification = "cache needs to be read and write by multiple objects")
class EventStreamObserver implements StreamObserver<EventStreamResponse> {
    private final Consumer<ProviderState> stateConsumer;
    private final Object sync;
    private final Cache cache;

    private static final String CONFIGURATION_CHANGE = "configuration_change";
    private static final String PROVIDER_READY = "provider_ready";
    static final String FLAGS_KEY = "flags";

    /**
     * Create a gRPC stream that get notified about flag changes.
     *
     * @param cache                cache to update
     * @param stateConsumer        lambda to call for setting the state
     * @param reconnectEventStream callback for trying to recreate the stream
     */
    EventStreamObserver(Object sync, Cache cache, Consumer<ProviderState> stateConsumer) {
        this.sync = sync;
        this.cache = cache;
        this.stateConsumer = stateConsumer;
    }

    @Override
    public void onNext(EventStreamResponse value) {
        switch (value.getType()) {
            case CONFIGURATION_CHANGE:
                this.handleConfigurationChangeEvent(value);
                break;
            case PROVIDER_READY:
                this.handleProviderReadyEvent();
                break;
            default:
                log.debug("unhandled event type {}", value.getType());
        }
    }

    @Override
    public void onError(Throwable t) {
        log.error("event stream", t);
        if (this.cache.getEnabled()) {
            this.cache.clear();
        }
        this.stateConsumer.accept(ProviderState.ERROR);
        synchronized (this.sync) {
            this.sync.notify();
        }
    }

    @Override
    public void onCompleted() {
        if (this.cache.getEnabled()) {
            this.cache.clear();
        }
        this.stateConsumer.accept(ProviderState.ERROR);
    }

    private void handleConfigurationChangeEvent(EventStreamResponse value) {
        this.stateConsumer.accept(ProviderState.READY);
        if (!this.cache.getEnabled()) {
            return;
        }
        Map<String, Value> data = value.getData().getFieldsMap();
        Value flagsValue = data.get(FLAGS_KEY);
        if (flagsValue == null) {
            this.cache.clear();
            return;
        }

        Map<String, Value> flags = flagsValue.getStructValue().getFieldsMap();
        for (String flagKey : flags.keySet()) {
            this.cache.remove(flagKey);
        }
    }

    private void handleProviderReadyEvent() {
        this.stateConsumer.accept(ProviderState.READY);
        if (this.cache.getEnabled()) {
            this.cache.clear();
        }
    }
}
