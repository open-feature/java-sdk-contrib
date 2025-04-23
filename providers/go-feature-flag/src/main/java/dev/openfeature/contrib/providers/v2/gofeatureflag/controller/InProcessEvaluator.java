package dev.openfeature.contrib.providers.v2.gofeatureflag.controller;

import dev.openfeature.contrib.providers.v2.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.v2.gofeatureflag.api.GoFeatureFlagApi;
import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.Flag;
import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.FlagConfigResponse;
import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.v2.gofeatureflag.exception.FlagConfigurationEndpointNotFound;
import dev.openfeature.contrib.providers.v2.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.v2.gofeatureflag.wasm.EvaluationWasm;
import dev.openfeature.contrib.providers.v2.gofeatureflag.wasm.bean.WasmInput;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEventDetails;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class InProcessEvaluator implements IEvaluator {
    /** API to contact GO Feature Flag. */
    private final GoFeatureFlagApi api;
    /** WASM evaluation engine. */
    private final EvaluationWasm evaluationEngine;
    /** Options to configure the provider. */
    private final GoFeatureFlagProviderOptions options;
    /** Method to call when we have a configuration change */
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

    public InProcessEvaluator(GoFeatureFlagApi api, GoFeatureFlagProviderOptions options,
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
        Map<String, Object> flagContext = new HashMap<>(Collections.emptyMap());
        flagContext.put("evaluationContextEnrichment", this.evaluationContextEnrichment);
        flagContext.put("defaultSdkValue", defaultValue);

        val wasmInput = WasmInput.builder()
                .evalContext(evaluationContext.asObjectMap())
                .flagContext(flagContext)
                .flag(this.flags.get(key))
                .flagKey(key)
                .build();

        return this.evaluationEngine.evaluate(wasmInput);
    }

    @Override
    public void init() {
        val configFlags = api.retrieveFlagConfiguration(this.etag);
        this.flags = configFlags.getFlags();
        this.etag = configFlags.getEtag();
        this.lastUpdate = configFlags.getLastUpdated();
        this.evaluationContextEnrichment = configFlags.getEvaluationContextEnrichment();
        // We call the WASM engine to avoid a cold start at the 1st evaluation
        this.evaluationEngine.evaluate(WasmInput.builder().build());

        // start the polling of the flag configuration
        this.configurationDisposable = startCheckFlagConfigurationChangesDaemon();
    }

    @Override
    public void destroy() {
        if (this.configurationDisposable != null) {
            this.configurationDisposable.dispose();
        }
        // TODO: kill the WASM process
        // TODO: stop the polling
    }

    /**
     * startCheckFlagConfigurationChangesDaemon is a daemon that will check if the flag configuration
     * has changed.
     *
     * @return Disposable - the subscription to the observable
     */
    @NotNull
    private Disposable startCheckFlagConfigurationChangesDaemon() {
        long pollingIntervalMs = options.getFlagChangePollingIntervalMs() != null
                ? options.getFlagChangePollingIntervalMs()
                : Const.DEFAULT_POLLING_CONFIG_FLAG_CHANGE_INTERVAL_MS;

        PublishSubject<Object> stopSignal = PublishSubject.create();
        Observable<Long> intervalObservable = Observable.interval(pollingIntervalMs, TimeUnit.MILLISECONDS);
        Observable<FlagConfigResponse> apiCallObservable = intervalObservable
                // as soon something is published in stopSignal, the interval will stop
                .takeUntil(stopSignal)
                .flatMap(tick -> Observable.fromCallable(() -> this.api.retrieveFlagConfiguration(this.etag))
                        .onErrorResumeNext(e -> {
                            log.error("error while calling flag configuration API", e);
                            if (e instanceof FlagConfigurationEndpointNotFound) {
                                // emit an item to stop the interval to stop the loop
                                stopSignal.onNext(new Object());
                            }
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
                    this.flags = response.getFlags();

                    // TODO: check if the evaluation context enrichment has changed
                    val changeDetails = ProviderEventDetails.builder()
                            .flagsChanged(new ArrayList<>(response.getFlags().keySet()))
                            .build();
                    this.emitProviderConfigurationChanged.accept(changeDetails);
                },
                throwable -> log.error("error while calling flag configuration API, error: {}",
                        throwable.getMessage()));
    }

}
