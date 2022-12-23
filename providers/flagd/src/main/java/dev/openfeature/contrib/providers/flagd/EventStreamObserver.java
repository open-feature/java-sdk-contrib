package dev.openfeature.contrib.providers.flagd;

import java.util.Map;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import dev.openfeature.flagd.grpc.Schema.EventStreamResponse;
import com.google.protobuf.Value;

/**
 * EventStreamObserver handles events emitted by flagd.
 */
@Slf4j
public class EventStreamObserver implements StreamObserver<EventStreamResponse> {
    private EventStreamCallback callback;
    private FlagdCache cache;

    private static final String configurationChange = "configuration_change";
    private static final String providerReady = "provider_ready";

    EventStreamObserver(FlagdCache cache, EventStreamCallback callback) {
        this.cache = cache;
        this.callback = callback;
    }

    @Override
    public void onNext(EventStreamResponse value) {
        switch (value.getType()) {
            case configurationChange:
                this.handleConfigurationChangeEvent(value);
                break;
            case providerReady:
                this.handleProviderReadyEvent();
                break;
            default:
                log.warn("unhandled event type {}", value.getType());
                return;
        }
    }

    @Override
    public void onError(Throwable t) {
        log.error("event stream", t);
        this.cache.clear();
        this.callback.setEventStreamAlive(false);
        try {
            this.callback.restartEventStream();
        } catch (Exception e) {
            log.error("restart event stream", e);
        }
    }

    @Override
    public void onCompleted() {
        this.cache.clear();
        this.callback.setEventStreamAlive(false);
    }

    private void handleConfigurationChangeEvent(EventStreamResponse value) {
        if (!this.cache.getEnabled()) {
            return;
        }

        Map<String, Value> data = value.getData().getFieldsMap();
        Value flagKeyValue = data.get("flagKey");
        String flagKey = flagKeyValue.getStringValue();

        this.cache.remove(flagKey);
    }

    private void handleProviderReadyEvent() {
        this.cache.clear();
        this.callback.setEventStreamAlive(true);
    }
}
