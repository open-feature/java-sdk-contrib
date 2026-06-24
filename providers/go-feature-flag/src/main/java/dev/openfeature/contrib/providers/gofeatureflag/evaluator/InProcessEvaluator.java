package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.api.GoFeatureFlagApi;
import dev.openfeature.contrib.providers.gofeatureflag.bean.Flag;
import dev.openfeature.contrib.providers.gofeatureflag.bean.FlagConfigResponse;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.WasmEvaluatorPool;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.FlagContext;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.WasmInput;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Reason;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
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
    /** Pool of WASM evaluation engine instances for thread-safe concurrent evaluation. */
    private final WasmEvaluatorPool evaluationPool;
    /** Options to configure the provider. */
    private final GoFeatureFlagProviderOptions options;
    /** Method to call when we have a configuration change. */
    private final Consumer<ProviderEventDetails> emitProviderConfigurationChanged;
    /** Immutable snapshot of all flag configuration state; updated atomically by the polling daemon. */
    private volatile EvaluatorState state;
    /** disposable which manage the polling of the flag configurations. */
    private Disposable configurationDisposable;

    private static final class EvaluatorState {
        final Map<String, Flag> flags;
        final Map<String, Object> evaluationContextEnrichment;
        final String etag;
        final Date lastUpdate;

        EvaluatorState(
                Map<String, Flag> flags,
                Map<String, Object> evaluationContextEnrichment,
                String etag,
                Date lastUpdate) {
            this.flags = flags;
            this.evaluationContextEnrichment = evaluationContextEnrichment;
            this.etag = etag;
            this.lastUpdate = lastUpdate;
        }
    }

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
        this.options = options;
        this.emitProviderConfigurationChanged = emitProviderConfigurationChanged;
        this.state = new EvaluatorState(Collections.emptyMap(), null, "", new Date(0));
        int poolSize = options.getWasmEvaluatorPoolSize() != null
                ? options.getWasmEvaluatorPoolSize()
                : Const.DEFAULT_WASM_EVALUATOR_POOL_SIZE;
        this.evaluationPool = new WasmEvaluatorPool(poolSize);
    }

    @Override
    public GoFeatureFlagResponse evaluate(String key, Object defaultValue, EvaluationContext evaluationContext) {
        EvaluatorState current = this.state;
        if (current.flags.get(key) == null) {
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
                        .evaluationContextEnrichment(current.evaluationContextEnrichment)
                        .build())
                .evalContext(evaluationContext.asObjectMap())
                .flag(current.flags.get(key))
                .flagKey(key)
                .build();
        return this.evaluationPool.evaluate(wasmInput);
    }

    @Override
    public boolean isFlagTrackable(final String flagKey) {
        Flag flag = this.state.flags.get(flagKey);
        return flag != null && (flag.getTrackEvents() == null || flag.getTrackEvents());
    }

    @Override
    public void init() {
        val configFlags = api.retrieveFlagConfiguration(this.state.etag, options.getEvaluationFlagList());
        this.state = new EvaluatorState(
                configFlags.getFlags(),
                configFlags.getEvaluationContextEnrichment(),
                configFlags.getEtag(),
                configFlags.getLastUpdated());

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

        Observable<Long> intervalObservable =
                Observable.interval(pollingIntervalMs, TimeUnit.MILLISECONDS, Schedulers.io());
        Observable<FlagConfigResponse> apiCallObservable = intervalObservable
                .flatMap(tick -> Observable.fromCallable(() ->
                                this.api.retrieveFlagConfiguration(this.state.etag, options.getEvaluationFlagList()))
                        .onErrorResumeNext(e -> {
                            log.error("error while calling flag configuration API", e);
                            return Observable.empty();
                        }))
                .subscribeOn(Schedulers.io());

        return apiCallObservable.subscribe(
                response -> {
                    EvaluatorState current = this.state;
                    if (response.getEtag().equals(current.etag)) {
                        log.debug("flag configuration has not changed: {}", response);
                        return;
                    }

                    if (response.getLastUpdated().before(current.lastUpdate)) {
                        log.info("configuration received is older than the current one");
                        return;
                    }

                    log.info("flag configuration has changed");
                    val flagChanges = findFlagConfigurationChanges(current.flags, response.getFlags());
                    this.state = new EvaluatorState(
                            response.getFlags(),
                            response.getEvaluationContextEnrichment(),
                            response.getEtag(),
                            response.getLastUpdated());
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
