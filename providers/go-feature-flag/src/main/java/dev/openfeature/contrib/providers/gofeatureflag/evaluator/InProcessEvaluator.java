package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import com.fasterxml.jackson.databind.JsonNode;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.api.GoFeatureFlagApi;
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
import java.util.Optional;
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
        final Map<String, JsonNode> flags;
        final Map<String, Object> evaluationContextEnrichment;
        final String etag;
        final Date lastUpdate;
        /**
         * true once a configuration has been retrieved and applied. A configuration carrying an empty
         * flag map counts as loaded: an empty configuration is a valid one, not a missing one.
         * The marker lives in the snapshot rather than beside it, so that readiness and the flag map
         * it describes can never be read out of step.
         */
        final boolean configurationLoaded;

        /** the state held until a configuration has been applied for the first time. */
        static EvaluatorState notLoaded() {
            return new EvaluatorState(Collections.emptyMap(), null, "", new Date(0), false);
        }

        EvaluatorState(
                Map<String, JsonNode> flags,
                Map<String, Object> evaluationContextEnrichment,
                String etag,
                Date lastUpdate) {
            // only reachable from a retrieved configuration, so it is loaded by construction
            this(flags, evaluationContextEnrichment, etag, lastUpdate, true);
        }

        private EvaluatorState(
                Map<String, JsonNode> flags,
                Map<String, Object> evaluationContextEnrichment,
                String etag,
                Date lastUpdate,
                boolean configurationLoaded) {
            this.flags = flags;
            this.evaluationContextEnrichment = evaluationContextEnrichment;
            this.etag = etag;
            this.lastUpdate = lastUpdate;
            this.configurationLoaded = configurationLoaded;
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
        this.state = EvaluatorState.notLoaded();
        int poolSize = options.getWasmEvaluatorPoolSize() != null
                ? options.getWasmEvaluatorPoolSize()
                : Const.DEFAULT_WASM_EVALUATOR_POOL_SIZE;
        this.evaluationPool = new WasmEvaluatorPool(poolSize);
    }

    @Override
    public GoFeatureFlagResponse evaluate(String key, Object defaultValue, EvaluationContext evaluationContext) {
        EvaluatorState current = this.state;
        // With no configuration ever loaded every key is absent, and answering FLAG_NOT_FOUND would
        // blame the caller's flag key for an infrastructure failure.
        if (!current.configurationLoaded) {
            val notReady = new GoFeatureFlagResponse();
            notReady.setReason(Reason.ERROR.name());
            notReady.setErrorCode(ErrorCode.PROVIDER_NOT_READY.name());
            notReady.setErrorDetails(
                    "impossible to evaluate flag " + key + ": no flag configuration has been loaded yet");
            notReady.setValue(defaultValue);
            return notReady;
        }
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
        // trackEvents is the only field of the flag configuration a provider may read: everything
        // else belongs to the evaluation engine and is passed through untouched.
        JsonNode flag = this.state.flags.get(flagKey);
        if (flag == null) {
            return false;
        }
        JsonNode trackEvents = flag.get(Const.FIELD_TRACK_EVENTS);
        return trackEvents == null || trackEvents.isNull() || trackEvents.asBoolean(true);
    }

    @Override
    public void init() {
        // We ensure that no polling is happening before starting the initialization.
        stopPolling();

        // an empty response means the configuration has not been modified, so the state we already
        // hold is still current and must not be overwritten.
        api.retrieveFlagConfiguration(this.state.etag, options.getEvaluationFlagList())
                .ifPresent(configFlags -> this.state = new EvaluatorState(
                        configFlags.getFlags(),
                        configFlags.getEvaluationContextEnrichment(),
                        configFlags.getEtag(),
                        configFlags.getLastUpdated()));

        // start the polling of the flag configuration
        this.configurationDisposable = startCheckFlagConfigurationChangesDaemon();
    }

    @Override
    public void destroy() {
        stopPolling();
    }

    /**
     * stopPolling cancels the configuration polling task, if one is running.
     * Once dispose() returns, the subscription is guaranteed to deliver no further emission to the
     * refresh consumer, so a request still in flight can no longer reach the configuration state. The
     * field is cleared so that the disposed subscription cannot be disposed, or mistaken for a live
     * one, a second time.
     */
    private synchronized void stopPolling() {
        if (this.configurationDisposable != null) {
            this.configurationDisposable.dispose();
            this.configurationDisposable = null;
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
                            return Observable.<Optional<FlagConfigResponse>>empty();
                        }))
                // a 304 emits an empty Optional: drop it here so the refresh consumer below is
                // structurally unable to write state for a response that carries no configuration.
                .filter(Optional::isPresent)
                .map(Optional::get)
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
            final Map<String, JsonNode> originalFlags, final Map<String, JsonNode> newFlags) {
        // this function should return a list of flags that have changed between the two maps
        // it should contain all updated, added and removed flags
        List<String> changedFlags = new ArrayList<>();

        // Find added or updated flags
        for (Map.Entry<String, JsonNode> entry : newFlags.entrySet()) {
            String key = entry.getKey();
            JsonNode newFlag = entry.getValue();
            JsonNode originalFlag = originalFlags.get(key);

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
