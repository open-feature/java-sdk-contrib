package dev.openfeature.contrib.providers.flagd.resolver.grpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import com.google.protobuf.Value;

import dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.Cache;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.EventStreamResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

/**
 * EventStreamObserver handles events emitted by flagd.
 */
@Slf4j
@SuppressFBWarnings(justification = "cache needs to be read and write by multiple objects")
class EventStreamObserver implements StreamObserver<EventStreamResponse> {
    private final BiConsumer<Boolean, List<String>> stateConsumer;
    private final Object sync;
    private final Cache cache;

    private static final String CONFIGURATION_CHANGE = "configuration_change";
    private static final String PROVIDER_READY = "provider_ready";
    static final String FLAGS_KEY = "flags";

    /**
     * Create a gRPC stream that get notified about flag changes.
     *
     * @param sync          synchronization object from caller
     * @param cache         cache to update
     * @param stateConsumer lambda to call for setting the state
     */
    EventStreamObserver(Object sync, Cache cache, BiConsumer<Boolean, List<String>> stateConsumer) {
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
        log.warn("event stream", t);
        if (this.cache.getEnabled()) {
            this.cache.clear();
        }
        this.stateConsumer.accept(false, Collections.emptyList());

        // handle last call of this stream
        handleEndOfStream();
    }

    @Override
    public void onCompleted() {
        if (this.cache.getEnabled()) {
            this.cache.clear();
        }
        this.stateConsumer.accept(false, Collections.emptyList());

        // handle last call of this stream
        handleEndOfStream();
    }

    private void handleConfigurationChangeEvent(EventStreamResponse value) {
        List<String> changedFlags = new ArrayList<>();
        boolean cachingEnabled = this.cache.getEnabled();

        Map<String, Value> data = value.getData().getFieldsMap();
        Value flagsValue = data.get(FLAGS_KEY);
        if (flagsValue == null) {
            if (cachingEnabled) {
                this.cache.clear();
            }
        } else {
            Map<String, Value> flags = flagsValue.getStructValue().getFieldsMap();
            this.cache.getEnabled();
            for (String flagKey : flags.keySet()) {
                changedFlags.add(flagKey);
                if (cachingEnabled) {
                    this.cache.remove(flagKey);
                }
            }
        }

        this.stateConsumer.accept(true, changedFlags);
    }

    private void handleProviderReadyEvent() {
        this.stateConsumer.accept(true, Collections.emptyList());
        if (this.cache.getEnabled()) {
            this.cache.clear();
        }
    }

    private void handleEndOfStream() {
        synchronized (this.sync) {
            this.sync.notifyAll();
        }
    }
}
