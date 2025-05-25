package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.api.GoFeatureFlagApi;
import dev.openfeature.contrib.providers.gofeatureflag.bean.Flag;
import dev.openfeature.contrib.providers.gofeatureflag.bean.FlagConfigResponse;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.EvaluationWasm;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.FlagContext;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.WasmInput;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Reason;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * InProcessEvaluator is a class that represents the evaluation of a feature flag
 * it calls an external WASM module to evaluate the feature flag.
 */
@Slf4j
public class InProcessEvaluator implements IEvaluator {
    /** API to contact GO Feature Flag. */
    private final GoFeatureFlagApi api;
    /** WASM evaluation engine. */
    private final EvaluationWasm evaluationEngine;
    /** Options to configure the provider. */
    private final GoFeatureFlagProviderOptions options;
    /** Method to call when we have a configuration change. */
    private final Consumer<ProviderEventDetails> emitProviderConfigurationChanged;
    /** Local copy of the flags' configuration. */
    private Map<String, Flag> flags;
    /** Evaluation context enrichment. */
    private Map<String, Object> evaluationContextEnrichment;
    /** Last hash of the flags' configuration. */
    private String etag;
    /** Last update of the flags' configuration. */
    private Date lastUpdate;
    /** disposable which manage the polling of the flag configurations. */
    private Disposable configurationDisposable;

    /**
     * Constructor of the InProcessEvaluator.
     *
     * @param api                              - API to contact GO Feature Flag
     * @param options                          - options to configure the provider
     * @param emitProviderConfigurationChanged - method to call when we have a configuration change
     */
    public InProcessEvaluator(
            GoFeatureFlagApi api,
            GoFeatureFlagProviderOptions options,
            Consumer<ProviderEventDetails> emitProviderConfigurationChanged) {
        this.api = api;
        this.flags = Collections.emptyMap();
        this.etag = "";
        this.options = options;
        this.lastUpdate = new Date(0);
        this.emitProviderConfigurationChanged = emitProviderConfigurationChanged;
        this.evaluationEngine = new EvaluationWasm();
    }

    @Override
    public GoFeatureFlagResponse evaluate(String key, Object defaultValue, EvaluationContext evaluationContext) {
        if (this.flags.get(key) == null) {
            val err = new GoFeatureFlagResponse();
            err.setReason(Reason.ERROR.name());
            err.setErrorCode(ErrorCode.FLAG_NOT_FOUND.name());
            err.setErrorDetails("Flag " + key + " was not found in your configuration");
            err.setValue(defaultValue);
            return err;
        }
        val wasmInput = WasmInput.builder()
                .flagContext(FlagContext.builder()
                        .defaultSdkValue(defaultValue)
                        .evaluationContextEnrichment(this.evaluationContextEnrichment)
                        .build())
                .evalContext(evaluationContext.asObjectMap())
                .flag(this.flags.get(key))
                .flagKey(key)
                .build();
        return this.evaluationEngine.evaluate(wasmInput);
    }

    @Override
    public boolean isFlagTrackable(final String flagKey) {
        Flag flag = this.flags.get(flagKey);
        return flag != null && (flag.getTrackEvents() == null || flag.getTrackEvents());
    }

    @Override
    public void init() {
        val configFlags = api.retrieveFlagConfiguration(this.etag, options.getEvaluationFlagList());
        this.flags = configFlags.getFlags();
        this.etag = configFlags.getEtag();
        this.lastUpdate = configFlags.getLastUpdated();
        this.evaluationContextEnrichment = configFlags.getEvaluationContextEnrichment();
        // We call the WASM engine to avoid a cold start at the 1st evaluation
        this.evaluationEngine.preWarmWasm();

        // start the polling of the flag configuration
        this.configurationDisposable = startCheckFlagConfigurationChangesDaemon();
    }

    @Override
    public void destroy() {
        if (this.configurationDisposable != null) {
            this.configurationDisposable.dispose();
        }
    }

    /**
     * startCheckFlagConfigurationChangesDaemon is a daemon that will check if the flag configuration has changed.
     *
     * @return Disposable - the subscription to the observable
     */
    private Disposable startCheckFlagConfigurationChangesDaemon() {
        long pollingIntervalMs = options.getFlagChangePollingIntervalMs() != null
                ? options.getFlagChangePollingIntervalMs()
                : Const.DEFAULT_POLLING_CONFIG_FLAG_CHANGE_INTERVAL_MS;

        PublishSubject<Object> stopSignal = PublishSubject.create();
        Observable<Long> intervalObservable = Observable.interval(pollingIntervalMs, TimeUnit.MILLISECONDS);
        Observable<FlagConfigResponse> apiCallObservable = intervalObservable
                // as soon something is published in stopSignal, the interval will stop
                .takeUntil(stopSignal)
                .flatMap(tick -> Observable.fromCallable(
                                () -> this.api.retrieveFlagConfiguration(this.etag, options.getEvaluationFlagList()))
                        .onErrorResumeNext(e -> {
                            log.error("error while calling flag configuration API", e);
                            return Observable.empty();
                        }))
                .subscribeOn(Schedulers.io());

        return apiCallObservable.subscribe(
                response -> {
                    if (response.getEtag().equals(this.etag)) {
                        log.debug("flag configuration has not changed: {}", response);
                        return;
                    }

                    if (response.getLastUpdated().before(this.lastUpdate)) {
                        log.info("configuration received is older than the current one");
                        return;
                    }

                    log.info("flag configuration has changed");
                    this.etag = response.getEtag();
                    this.lastUpdate = response.getLastUpdated();
                    val flagChanges = findFlagConfigurationChanges(this.flags, response.getFlags());
                    this.flags = response.getFlags();
                    this.evaluationContextEnrichment = response.getEvaluationContextEnrichment();
                    val changeDetails = ProviderEventDetails.builder()
                            .flagsChanged(flagChanges)
                            .message("flag configuration has changed")
                            .build();
                    this.emitProviderConfigurationChanged.accept(changeDetails);
                },
                throwable ->
                        log.error("error while calling flag configuration API, error: {}", throwable.getMessage()));
    }

    /**
     * findFlagConfigurationChanges is a function that will find the flags that have changed.
     *
     * @param originalFlags - list of original flags
     * @param newFlags      - list of new flags
     * @return - list of flags that have changed
     */
    private List<String> findFlagConfigurationChanges(
            final Map<String, Flag> originalFlags, final Map<String, Flag> newFlags) {
        // this function should return a list of flags that have changed between the two maps
        // it should contain all updated, added and removed flags
        List<String> changedFlags = new ArrayList<>();

        // Find added or updated flags
        for (Map.Entry<String, Flag> entry : newFlags.entrySet()) {
            String key = entry.getKey();
            Flag newFlag = entry.getValue();
            Flag originalFlag = originalFlags.get(key);

            if (originalFlag == null || !originalFlag.equals(newFlag)) {
                changedFlags.add(key);
            }
        }

        // Find removed flags
        for (String key : originalFlags.keySet()) {
            if (!newFlags.containsKey(key)) {
                changedFlags.add(key);
            }
        }

        return changedFlags;
    }
}
