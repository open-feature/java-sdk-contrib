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
import dev.openfeature.contrib.providers.gofeatureflag.service.EvaluationService;
import dev.openfeature.contrib.providers.gofeatureflag.service.EventsPublisher;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.util.EvaluationContextUtil;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Tracking;
import dev.openfeature.sdk.TrackingEventDetails;
import dev.openfeature.sdk.Value;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * GoFeatureFlagProvider is the JAVA provider implementation for the feature flag solution GO Feature Flag.
 */
@Slf4j
public final class GoFeatureFlagProvider extends EventProvider implements Tracking {
    /** Options to configure the provider. */
    private final GoFeatureFlagProviderOptions options;
    /** Service to evaluate the flags. */
    private final EvaluationService evalService;
    /** List of the hooks used by the provider. */
    private final List<Hook> hooks = new ArrayList<>();
    /** API layer to contact GO Feature Flag. */
    private final GoFeatureFlagApi api;
    /** EventPublisher is the system collecting all the information to send to GO Feature Flag. */
    private final EventsPublisher<IEvent> eventsPublisher;
    /** exporter metadata contains the metadata that we want to send to the exporter. */
    private final Map<String, Object> exporterMetadata;
    /** DataCollectorHook is the hook to send usage of the flags. */
    private DataCollectorHook dataCollectorHook;

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
        this.evalService = new EvaluationService(getEvaluator(this.api));

        long flushIntervalMs =
                (options.getFlushIntervalMs() == null) ? Const.DEFAULT_FLUSH_INTERVAL_MS : options.getFlushIntervalMs();
        int maxPendingEvents = (options.getMaxPendingEvents() == null)
                ? Const.DEFAULT_MAX_PENDING_EVENTS
                : options.getMaxPendingEvents();
        Consumer<List<IEvent>> publisher = this::publishEvents;
        this.eventsPublisher = new EventsPublisher<>(publisher, flushIntervalMs, maxPendingEvents);

        if (options.getExporterMetadata() == null) {
            this.exporterMetadata = new HashMap<>();
        } else {
            val exp = new HashMap<>(options.getExporterMetadata());
            exp.put("provider", "java");
            exp.put("openfeature", true);
            this.exporterMetadata = exp;
        }
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
        return this.evalService.getEvaluation(key, defaultValue, evaluationContext, Boolean.class);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(
            String key, String defaultValue, EvaluationContext evaluationContext) {
        return this.evalService.getEvaluation(key, defaultValue, evaluationContext, String.class);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(
            String key, Integer defaultValue, EvaluationContext evaluationContext) {
        return this.evalService.getEvaluation(key, defaultValue, evaluationContext, Integer.class);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(
            String key, Double defaultValue, EvaluationContext evaluationContext) {
        return this.evalService.getEvaluation(key, defaultValue, evaluationContext, Double.class);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(
            String key, Value defaultValue, EvaluationContext evaluationContext) {
        return this.evalService.getEvaluation(key, defaultValue, evaluationContext, Value.class);
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        super.initialize(evaluationContext);
        this.evalService.init();
        this.hooks.add(new EnrichEvaluationContextHook(this.options.getExporterMetadata()));
        // In case of remote evaluation, we don't need to send the data to the collector
        // because the relay-proxy will collect events directly server side.
        if (!this.options.isDisableDataCollection() && this.options.getEvaluationType() != EvaluationType.REMOTE) {
            this.dataCollectorHook = new DataCollectorHook(DataCollectorHookOptions.builder()
                    .eventsPublisher(this.eventsPublisher)
                    .collectUnCachedEvaluation(true)
                    .evalService(this.evalService)
                    .build());

            this.hooks.add(this.dataCollectorHook);
        }
        log.info("finishing initializing provider");
    }

    @Override
    public void shutdown() {
        super.shutdown();
        this.evalService.destroy();
        if (this.dataCollectorHook != null) {
            this.dataCollectorHook.shutdown();
        }
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
        val trackingEvent = TrackingEvent.builder()
                .evaluationContext((context != null) ? context.asObjectMap() : Collections.emptyMap())
                .userKey(context != null ? context.getTargetingKey() : "undefined-targetingKey")
                .contextKind(EvaluationContextUtil.isAnonymousUser(context) ? "anonymousUser" : "user")
                .kind("tracking")
                .key(eventName)
                .trackingEventDetails(details != null ? details.asObjectMap() : Collections.emptyMap())
                .creationDate(System.currentTimeMillis() / 1000L)
                .build();
        this.eventsPublisher.add(trackingEvent);
    }

    /**
     * Get the evaluator based on the evaluation type.
     * It will initialize the evaluator based on the evaluation type.
     *
     * @return the evaluator
     */
    private IEvaluator getEvaluator(GoFeatureFlagApi api) {
        // Select the evaluator based on the evaluation type
        if (options.getEvaluationType() == null || options.getEvaluationType() == EvaluationType.IN_PROCESS) {
            Consumer<ProviderEventDetails> emitProviderConfigurationChanged = this::emitProviderConfigurationChanged;
            return new InProcessEvaluator(api, this.options, emitProviderConfigurationChanged);
        }
        return new RemoteEvaluator(api);
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
