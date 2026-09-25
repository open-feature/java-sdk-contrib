package dev.openfeature.contrib.providers.gofeatureflag;

import dev.openfeature.contrib.providers.gofeatureflag.api.GoFeatureFlagApi;
import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.gofeatureflag.bean.IEvent;
import dev.openfeature.contrib.providers.gofeatureflag.bean.TrackingEvent;
import dev.openfeature.contrib.providers.gofeatureflag.evaluator.IEvaluator;
import dev.openfeature.contrib.providers.gofeatureflag.evaluator.InProcessEvaluator;
import dev.openfeature.contrib.providers.gofeatureflag.evaluator.RemoteEvaluator;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.hook.DataCollectorHook;
import dev.openfeature.contrib.providers.gofeatureflag.hook.DataCollectorHookOptions;
import dev.openfeature.contrib.providers.gofeatureflag.hook.EnrichEvaluationContextHook;
import dev.openfeature.contrib.providers.gofeatureflag.service.EventsPublisher;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.util.EvaluationContextUtil;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Tracking;
import dev.openfeature.sdk.TrackingEventDetails;
import dev.openfeature.sdk.Value;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * GoFeatureFlagProvider is the JAVA provider implementation for the feature flag management system GO Feature Flag.
 */
@Slf4j
public final class GoFeatureFlagProvider extends EventProvider implements Tracking {
    /** Options to configure the provider. */
    private final GoFeatureFlagProviderOptions options;
    /** Service to evaluate the flags. */
    private final IEvaluator evaluator;
    /** List of the hooks used by the provider. */
    private final List<Hook> hooks = new ArrayList<>();
    /** API layer to contact GO Feature Flag. */
    private final GoFeatureFlagApi api;
    /** EventPublisher is the system collecting all the information to send to GO Feature Flag. */
    private final EventsPublisher<IEvent> eventsPublisher;
    /** exporter metadata contains the metadata that we want to send to the exporter. */
    private final Map<String, Object> exporterMetadata;

    /**
     * Constructor of the provider.
     *
     * @param options - options to configure the provider
     * @throws InvalidOptions - if options are invalid
     */
    public GoFeatureFlagProvider(final GoFeatureFlagProviderOptions options) throws InvalidOptions {
        if (options == null) {
            throw new InvalidOptions("No options provided");
        }
        options.validate();
        this.options = options;
        this.api = GoFeatureFlagApi.builder().options(options).build();
        this.evaluator = getEvaluator();

        Consumer<List<IEvent>> publisher = this::publishEvents;
        this.eventsPublisher =
                new EventsPublisher<>(publisher, options.getFlushIntervalMs(), options.getMaxPendingEvents());

        val exp = new HashMap<>(options.getExporterMetadata());
        exp.put(Const.METADATA_PROVIDER, "java");
        exp.put(Const.METADATA_OPENFEATURE, true);
        this.exporterMetadata = exp;
    }

    @Override
    public Metadata getMetadata() {
        return () -> "GO Feature Flag Provider";
    }

    @Override
    public List<Hook> getProviderHooks() {
        return new ArrayList<>(this.hooks);
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(
            String key, Boolean defaultValue, EvaluationContext evaluationContext) {
        return this.evaluator.getBooleanEvaluation(key, defaultValue, evaluationContext);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(
            String key, String defaultValue, EvaluationContext evaluationContext) {
        return this.evaluator.getStringEvaluation(key, defaultValue, evaluationContext);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(
            String key, Integer defaultValue, EvaluationContext evaluationContext) {
        return this.evaluator.getIntegerEvaluation(key, defaultValue, evaluationContext);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(
            String key, Double defaultValue, EvaluationContext evaluationContext) {
        return this.evaluator.getDoubleEvaluation(key, defaultValue, evaluationContext);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(
            String key, Value defaultValue, EvaluationContext evaluationContext) {
        return this.evaluator.getObjectEvaluation(key, defaultValue, evaluationContext);
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        this.initialize(evaluationContext, "");
    }

    @Override
    public void initialize(EvaluationContext evaluationContext, String domain) throws Exception {
        super.initialize(evaluationContext);
        // re-initialization must reset the publisher: its shutdown flag and its scheduler are both
        // one-shot, so without this a provider that is shut down and initialized again never flushes.
        this.eventsPublisher.start();
        this.evaluator.initialize(evaluationContext, domain);
        this.hooks.clear();
        this.hooks.add(new EnrichEvaluationContextHook(this.exporterMetadata));
        // In case of remote evaluation, we don't need to send the data to the collector
        // because the relay-proxy will collect events directly server side.
        if (!this.options.isDisableDataCollection() && this.options.getEvaluationType() != EvaluationType.REMOTE) {
            this.hooks.add(new DataCollectorHook(DataCollectorHookOptions.builder()
                    .eventsPublisher(this.eventsPublisher)
                    .collectUnCachedEvaluation(true)
                    .evaluator(this.evaluator)
                    .build()));
        }
        log.info("finishing initializing provider");
    }

    @Override
    public void shutdown() {
        super.shutdown();
        this.evaluator.shutdown();
        this.eventsPublisher.shutdown();
    }

    @Override
    public void track(final String eventName) {
        this.track(eventName, null, null);
    }

    @Override
    public void track(final String eventName, final EvaluationContext evaluationContext) {
        this.track(eventName, evaluationContext, null);
    }

    @Override
    public void track(final String eventName, final TrackingEventDetails trackingEventDetails) {
        this.track(eventName, null, trackingEventDetails);
    }

    @Override
    public void track(final String eventName, final EvaluationContext context, final TrackingEventDetails details) {
        if (this.options.isDisableDataCollection()) {
            return;
        }

        val trackingEvent = TrackingEvent.builder()
                .evaluationContext((context != null) ? context.asObjectMap() : Collections.emptyMap())
                .userKey(EvaluationContextUtil.userKey(context))
                .contextKind(EvaluationContextUtil.contextKind(context))
                .kind("tracking")
                .key(eventName)
                .trackingEventDetails(details != null ? details.asObjectMap() : Collections.emptyMap())
                .creationDate(System.currentTimeMillis() / 1000L)
                .build();
        this.eventsPublisher.add(trackingEvent);
    }

    /**
     * Get the evaluator based on the evaluation type.
     *
     * @return the evaluator
     */
    private IEvaluator getEvaluator() {
        // Select the evaluator based on the evaluation type
        BiConsumer<ProviderEvent, ProviderEventDetails> emitter = this::emit;
        if (options.getEvaluationType() == null || options.getEvaluationType() == EvaluationType.IN_PROCESS) {
            return new InProcessEvaluator(this.api, this.options, emitter);
        }
        return new RemoteEvaluator(this.options, emitter);
    }

    /**
     * publishEvents is calling the GO Feature Flag data/collector api to store the flag usage for
     * analytics.
     *
     * @param eventsList - list of the event to send to GO Feature Flag
     */
    private void publishEvents(List<IEvent> eventsList) {
        this.api.sendEventToDataCollector(eventsList, this.exporterMetadata);
    }
}
