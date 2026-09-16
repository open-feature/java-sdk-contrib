package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import static dev.openfeature.sdk.Value.objectToValue;

import com.fasterxml.jackson.databind.JsonNode;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.api.GoFeatureFlagApi;
import dev.openfeature.contrib.providers.gofeatureflag.bean.FlagConfigResponse;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.util.MetadataUtil;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.WasmEvaluatorPool;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.FlagContext;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.WasmInput;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.ExceptionUtils;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
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

    private GoFeatureFlagResponse evaluate(String key, Object defaultValue, EvaluationContext evaluationContext) {
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
    public void initialize(EvaluationContext ctx, String domain) throws Exception {
        this.initialize(ctx);
    }

    @Override
    public void initialize(EvaluationContext ctx) throws Exception {
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
    public void shutdown() {
        stopPolling();
        this.evaluationPool.close();
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return genericEvaluation(key, defaultValue, ctx, Boolean.class);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return genericEvaluation(key, defaultValue, ctx, String.class);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return genericEvaluation(key, defaultValue, ctx, Integer.class);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return genericEvaluation(key, defaultValue, ctx, Double.class);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return genericEvaluation(key, defaultValue, ctx, Value.class);
    }

    /**
     * genericEvaluation evaluates the flag and converts the engine response into the resolution
     * structure expected by the OpenFeature SDK.
     *
     * @param key          - name of the flag
     * @param defaultValue - default value provided by the caller
     * @param ctx          - evaluation context
     * @param expectedType - type the resolver called by the SDK is contracted to return
     * @param <T>          - type of the flag value
     * @return the evaluation result for this flag
     */
    private <T> ProviderEvaluation<T> genericEvaluation(
            final String key, final T defaultValue, final EvaluationContext ctx, final Class<?> expectedType) {
        return toProviderEvaluation(key, defaultValue, this.evaluate(key, defaultValue, ctx), expectedType);
    }

    /**
     * toProviderEvaluation converts an engine response into the resolution structure expected by
     * the OpenFeature SDK.
     *
     * <p>It is separate from genericEvaluation so that the conversion can be specified against a
     * response directly, independently of what the WASM engine can be made to emit.
     *
     * @param key          - name of the flag
     * @param defaultValue - default value provided by the caller
     * @param response     - response returned by the evaluation engine
     * @param expectedType - type the resolver called by the SDK is contracted to return
     * @param <T>          - type of the flag value
     * @return the evaluation result for this flag
     */
    static <T> ProviderEvaluation<T> toProviderEvaluation(
            final String key, final T defaultValue, final GoFeatureFlagResponse response, final Class<?> expectedType) {
        if (response.getErrorCode() != null && !response.getErrorCode().isEmpty()) {
            throw ExceptionUtils.instantiateErrorByErrorCode(
                    mapErrorCode(response.getErrorCode()), response.getErrorDetails());
        }

        if (Reason.DISABLED.name().equalsIgnoreCase(response.getReason())) {
            // we don't set a variant since we are using the default value,
            // and we are not able to know which variant it is.
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .variant(response.getVariationType())
                    .reason(Reason.DISABLED.name())
                    .build();
        }

        if (response.getValue() == null) {
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason(Reason.DEFAULT.name())
                    .flagMetadata(MetadataUtil.convertFlagMetadata(response.getMetadata()))
                    .build();
        }

        T flagValue = convertValue(response.getValue(), expectedType);
        if (flagValue.getClass() != expectedType) {
            throw new TypeMismatchError(String.format(
                    "Flag value %s had unexpected type %s, expected %s.", key, flagValue.getClass(), expectedType));
        }

        return ProviderEvaluation.<T>builder()
                .reason(response.getReason())
                .value(flagValue)
                .variant(response.getVariationType())
                .flagMetadata(MetadataUtil.convertFlagMetadata(response.getMetadata()))
                .build();
    }

    /**
     * convertValue is converting the value returned by the evaluation engine in the right type.
     *
     * @param value        - the value we have received
     * @param expectedType - the type we expect for this value
     * @param <T>          - the type we want to convert to
     * @return a converted object
     */
    // The cast to T is unchecked on purpose: toProviderEvaluation compares the runtime class with
    // expectedType right after and raises a TypeMismatchError if the engine returned another type.
    @SuppressWarnings("unchecked")
    private static <T> T convertValue(final Object value, final Class<?> expectedType) {
        boolean isPrimitive = expectedType == Boolean.class
                || expectedType == String.class
                || expectedType == Integer.class
                || expectedType == Double.class;

        if (isPrimitive) {
            // JSON does not distinguish 100 from 100.0, and a number too large for an int decodes to
            // Long, so the float resolver accepts any number rather than only Integer.
            if (expectedType == Double.class && value instanceof Number) {
                return (T) Double.valueOf(((Number) value).doubleValue());
            }
            return (T) value;
        }
        return (T) objectToValue(value);
    }

    /**
     * mapErrorCode is mapping the error code in string received from the evaluation engine to the
     * SDK ErrorCode enum.
     *
     * @param errorCode - string of the error code received from the evaluation engine
     * @return an item from the enum, null if the engine reported no error
     */
    private static ErrorCode mapErrorCode(final String errorCode) {
        if (errorCode == null || errorCode.isEmpty()) {
            return null;
        }

        try {
            return ErrorCode.valueOf(errorCode);
        } catch (IllegalArgumentException e) {
            // an error the SDK does not know about, such as GO Feature Flag's own FLAG_CONFIG, is
            // still an error: reporting it as GENERAL keeps the evaluation from looking successful.
            return ErrorCode.GENERAL;
        }
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
