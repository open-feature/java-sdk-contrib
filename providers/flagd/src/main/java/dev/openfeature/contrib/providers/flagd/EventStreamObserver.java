package dev.openfeature.contrib.providers.flagd;

import java.util.Map;
import io.grpc.stub.StreamObserver;
import dev.openfeature.flagd.grpc.Schema.EventStreamResponse;
import dev.openfeature.sdk.ProviderEvaluation;
import com.google.protobuf.Value;

/**
 * EventStreamObserver handles events emitted by flagd.
 */
public class EventStreamObserver implements StreamObserver<EventStreamResponse> {
    private EventStreamCallback callback;
    private Boolean cacheEnabled;
    private Map<String, ProviderEvaluation<Boolean>> booleanCache;
    private Map<String, ProviderEvaluation<String>> stringCache;
    private Map<String, ProviderEvaluation<Double>> doubleCache;
    private Map<String, ProviderEvaluation<Integer>> integerCache;
    private Map<String, ProviderEvaluation<dev.openfeature.sdk.Value>> objectCache;


    static final String CONFIGURATION_CHANGE = "configuration_change";
    static final String PROVIDER_READY = "provider_ready";

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
            case CONFIGURATION_CHANGE:
                this.handleConfigurationChangeEvent(value);
                break;
            case PROVIDER_READY:
                this.handleProviderReadyEvent();
                break;
            default:
                // log
                return;
        }
    }

    @Override
    public void onError(Throwable t) {
        t.printStackTrace();
        this.purgeCache();
        this.callback.setEventStreamAlive(false);
        try {
            this.callback.restartEventStream();
        } catch (Exception e) {
            e.printStackTrace();
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
