package dev.openfeature.contrib.providers.flagd;

import java.util.Map;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import dev.openfeature.flagd.grpc.Schema.EventStreamResponse;
import dev.openfeature.sdk.ProviderEvaluation;
import com.google.protobuf.Value;

/**
 * EventStreamObserver handles events emitted by flagd.
 */
@Slf4j
public class EventStreamObserver implements StreamObserver<EventStreamResponse> {
    private EventStreamCallback callback;
    private Boolean cacheEnabled;
    private Map<String, ProviderEvaluation<Boolean>> booleanCache;
    private Map<String, ProviderEvaluation<String>> stringCache;
    private Map<String, ProviderEvaluation<Double>> doubleCache;
    private Map<String, ProviderEvaluation<Integer>> integerCache;
    private Map<String, ProviderEvaluation<dev.openfeature.sdk.Value>> objectCache;

    private static final String configurationChange = "configuration_change";
    private static final String providerReady = "provider_ready";

    EventStreamObserver(
        Boolean cacheEnabled, Map<String, ProviderEvaluation<Boolean>> booleanCache,
        Map<String, ProviderEvaluation<String>> stringCache, Map<String, ProviderEvaluation<Double>> doubleCache,
        Map<String, ProviderEvaluation<Integer>> integerCache,
        Map<String, ProviderEvaluation<dev.openfeature.sdk.Value>> objectCache, EventStreamCallback callback
    ) {
        this.cacheEnabled = cacheEnabled;
        this.booleanCache = booleanCache;
        this.stringCache = stringCache;
        this.doubleCache = doubleCache;
        this.integerCache = integerCache;
        this.objectCache = objectCache;
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
        this.purgeCache();
        this.callback.setEventStreamAlive(false);
        try {
            this.callback.restartEventStream();
        } catch (Exception e) {
            log.error("restart event stream", e);
        }
    }

    @Override
    public void onCompleted() {
        this.purgeCache();
        this.callback.setEventStreamAlive(false);
    }

    private void handleConfigurationChangeEvent(EventStreamResponse value) {
        if (!this.cacheEnabled) {
            return;
        }

        Map<String, Value> data = value.getData().getFieldsMap();
        Value flagKeyValue = data.get("flagKey");
        String flagKey = flagKeyValue.getStringValue();

        this.booleanCache.remove(flagKey);
        this.stringCache.remove(flagKey);
        this.doubleCache.remove(flagKey);
        this.integerCache.remove(flagKey);
        this.objectCache.remove(flagKey);
    }

    private void handleProviderReadyEvent() {
        this.purgeCache();
        this.callback.setEventStreamAlive(true);
    }
    
    private void purgeCache() {
        this.booleanCache.clear();
        this.stringCache.clear();
        this.doubleCache.clear();
        this.integerCache.clear();
        this.objectCache.clear();
    }
}
