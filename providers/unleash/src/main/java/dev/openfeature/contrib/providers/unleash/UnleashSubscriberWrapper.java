package dev.openfeature.contrib.providers.unleash;

import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.ProviderEventDetails;
import io.getunleash.FeatureToggle;
import io.getunleash.UnleashException;
import io.getunleash.event.ImpressionEvent;
import io.getunleash.event.ToggleEvaluated;
import io.getunleash.event.UnleashEvent;
import io.getunleash.event.UnleashReady;
import io.getunleash.event.UnleashSubscriber;
import io.getunleash.metric.ClientMetrics;
import io.getunleash.metric.ClientRegistration;
import io.getunleash.repository.FeatureCollection;
import io.getunleash.repository.FeatureToggleResponse;
import io.getunleash.repository.ToggleCollection;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * UnleashSubscriber wrapper for emitting event provider events.
 */
@Slf4j
@Generated
public class UnleashSubscriberWrapper implements UnleashSubscriber {

    private UnleashSubscriber unleashSubscriber;
    private EventProvider eventProvider;

    /**
     * Constructor.
     * @param unleashSubscriber subscriber
     * @param eventProvider events provider for emitting events.
     */
    public UnleashSubscriberWrapper(@Nullable UnleashSubscriber unleashSubscriber, EventProvider eventProvider) {
        this.unleashSubscriber = unleashSubscriber;
        this.eventProvider = eventProvider;
    }

    @Override
    public void onError(UnleashException unleashException) {
        unleashSubscriber.onError(unleashException);
        log.info("unleashException: ", unleashException);

        // Not emitting provider error, since some unleashException not expects to change provider state to error
    }

    @Override
    public void on(UnleashEvent unleashEvent) {
        unleashSubscriber.on(unleashEvent);
    }

    @Override
    public void onReady(UnleashReady unleashReady) {
        unleashSubscriber.onReady(unleashReady);
        eventProvider.emitProviderReady(ProviderEventDetails.builder()
            .eventMetadata(ImmutableMetadata.builder()
                .build()).build());
    }

    @Override
    public void toggleEvaluated(ToggleEvaluated toggleEvaluated) {
        unleashSubscriber.toggleEvaluated(toggleEvaluated);
    }

    @Override
    public void togglesFetched(FeatureToggleResponse toggleResponse) {
        unleashSubscriber.togglesFetched(toggleResponse);
        if (FeatureToggleResponse.Status.CHANGED.equals(toggleResponse.getStatus())) {
            List<String> flagsChanged = new ArrayList<>();
            for (FeatureToggle featureToggle : toggleResponse.getToggleCollection().getFeatures()) {
                flagsChanged.add(featureToggle.getName());
            }
            eventProvider.emitProviderConfigurationChanged(ProviderEventDetails.builder()
                .flagsChanged(flagsChanged)
                .eventMetadata(ImmutableMetadata.builder()
                    .build()).build());
        }
    }

    @Override
    public void clientMetrics(ClientMetrics clientMetrics) {
        unleashSubscriber.clientMetrics(clientMetrics);
    }

    @Override
    public void clientRegistered(ClientRegistration clientRegistration) {
        unleashSubscriber.clientRegistered(clientRegistration);
    }

    @Override
    public void togglesBackedUp(ToggleCollection toggleCollection) {
        unleashSubscriber.togglesBackedUp(toggleCollection);
    }

    @Override
    public void toggleBackupRestored(ToggleCollection toggleCollection) {
        unleashSubscriber.toggleBackupRestored(toggleCollection);
    }

    @Override
    public void togglesBootstrapped(ToggleCollection toggleCollection) {
        unleashSubscriber.togglesBootstrapped(toggleCollection);
    }

    @Override
    public void featuresBootstrapped(FeatureCollection featureCollection) {
        unleashSubscriber.featuresBootstrapped(featureCollection);
    }

    @Override
    public void featuresBackedUp(FeatureCollection featureCollection) {
        unleashSubscriber.featuresBackedUp(featureCollection);
    }

    @Override
    public void featuresBackupRestored(FeatureCollection featureCollection) {
        unleashSubscriber.featuresBackupRestored(featureCollection);
    }

    @Override
    public void impression(ImpressionEvent impressionEvent) {
        unleashSubscriber.impression(impressionEvent);
    }
}
