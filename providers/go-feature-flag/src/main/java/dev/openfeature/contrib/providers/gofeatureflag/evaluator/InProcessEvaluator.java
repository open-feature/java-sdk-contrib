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
import dev.openfeature.sdk.ProviderEvent;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * InProcessEvaluator is a class that represents the evaluation of a feature flag
 * it calls an external WASM module to evaluate the feature flag.
 */
@Slf4j
public class InProcessEvaluator implements IEvaluator {
    /**
     * Raw engine error codes that make the engine, rather than the flag, the suspect: the relay proxy
     * runs the same engine against the same flag and may well answer correctly. FLAG_CONFIG is
     * deliberately absent, being a misconfiguration the relay proxy would reproduce identically.
     */
    private static final Set<String> FALLBACK_TRIGGERS = Set.of(ErrorCode.PARSE_ERROR.name(), ErrorCode.GENERAL.name());

    /** API to contact GO Feature Flag. */
    private final GoFeatureFlagApi api;
    /** Pool of WASM evaluation engine instances for thread-safe concurrent evaluation. */
    private final WasmEvaluatorPool evaluationPool;
    /** Options to configure the provider. */
    private final GoFeatureFlagProviderOptions options;
    /** Method to call to emit a provider event to the SDK. */
    private final BiConsumer<ProviderEvent, ProviderEventDetails> emitter;
    /** Immutable snapshot of all flag configuration state; updated atomically by the polling daemon. */
    private volatile EvaluatorState state;
    /** disposable which manage the polling of the flag configurations. */
    private Disposable configurationDisposable;
    /** Number of refreshes that have failed in a row, reset by any successful one. */
    private final AtomicInteger consecutiveRefreshFailures = new AtomicInteger();
    /** true between the announcement of a stale configuration and the announcement of its end. */
    private final AtomicBoolean staleAnnounced = new AtomicBoolean();
    /** Builds the evaluator a failed local evaluation falls back to. */
    private final IEvaluator fallbackEvaluator;

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
     * @param api     - API to contact GO Feature Flag
     * @param options - options to configure the provider
     * @param emitter - method to call to emit a provider event to the SDK
     */
    public InProcessEvaluator(
            GoFeatureFlagApi api,
            GoFeatureFlagProviderOptions options,
            BiConsumer<ProviderEvent, ProviderEventDetails> emitter) {
        this.api = api;
        this.options = options;
        this.emitter = emitter;
        this.fallbackEvaluator = new RemoteEvaluator(options, emitter);
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
        // this fetch is a successful refresh too, and it bypasses the polling chain entirely. The
        // SDK announces readiness itself once initialize() returns, so nothing is emitted here.
        this.consecutiveRefreshFailures.set(0);
        this.staleAnnounced.set(false);

        // start the polling of the flag configuration
        this.configurationDisposable = startCheckFlagConfigurationChangesDaemon();
    }

    @Override
    public void shutdown() {
        stopPolling();
        this.fallbackEvaluator.shutdown();
        this.evaluationPool.close();
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return genericEvaluation(key, defaultValue, ctx, Boolean.class, IEvaluator::getBooleanEvaluation);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return genericEvaluation(key, defaultValue, ctx, String.class, IEvaluator::getStringEvaluation);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return genericEvaluation(key, defaultValue, ctx, Integer.class, IEvaluator::getIntegerEvaluation);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return genericEvaluation(key, defaultValue, ctx, Double.class, IEvaluator::getDoubleEvaluation);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return genericEvaluation(key, defaultValue, ctx, Value.class, IEvaluator::getObjectEvaluation);
    }

    /**
     * genericEvaluation evaluates the flag and converts the engine response into the resolution
     * structure expected by the OpenFeature SDK.
     *
     * <p>When the engine reports a failure that points at this provider rather than at the flag, the
     * relay proxy is asked instead and its answer is the one the caller receives.</p>
     *
     * @param key            - name of the flag
     * @param defaultValue   - default value provided by the caller
     * @param ctx            - evaluation context
     * @param expectedType   - type the resolver called by the SDK is contracted to return
     * @param remoteResolver - resolver of the fallback evaluator matching the type asked for
     * @param <T>            - type of the flag value
     * @return the evaluation result for this flag
     */
    private <T> ProviderEvaluation<T> genericEvaluation(
            final String key,
            final T defaultValue,
            final EvaluationContext ctx,
            final Class<?> expectedType,
            final RemoteResolver<T> remoteResolver) {
        val response = this.evaluate(key, defaultValue, ctx);
        // the raw code the engine emitted, read before toProviderEvaluation maps it onto the SDK
        // enumeration, where GO Feature Flag's own codes are folded into GENERAL and stop being
        // distinguishable from an engine failure.
        if (FALLBACK_TRIGGERS.contains(response.getErrorCode())) {
            val remote = evaluateRemotely(key, defaultValue, ctx, remoteResolver);
            if (remote.isPresent()) {
                return remote.get();
            }
        }
        return toProviderEvaluation(key, defaultValue, response, expectedType);
    }

    /**
     * evaluateRemotely asks the relay proxy about a flag the engine could not evaluate.
     *
     * <p>An empty result means the relay proxy could not answer either, in which case the caller is
     * owed the engine's error rather than the proxy's: the engine failing is the root cause, and the
     * proxy merely failed to make up for it. The remote failure is logged here because it is about to
     * disappear from the answer entirely.</p>
     *
     * @param key            - name of the flag
     * @param defaultValue   - default value provided by the caller
     * @param ctx            - evaluation context
     * @param remoteResolver - resolver of the fallback evaluator matching the type asked for
     * @param <T>            - type of the flag value
     * @return the relay proxy's answer, or empty if it could not give one
     */
    private <T> Optional<ProviderEvaluation<T>> evaluateRemotely(
            final String key,
            final T defaultValue,
            final EvaluationContext ctx,
            final RemoteResolver<T> remoteResolver) {
        try {
            val remote = remoteResolver.resolve(this.fallbackEvaluator, key, defaultValue, ctx);
            if (remote.getErrorCode() == null) {
                return Optional.of(markEvaluatedRemotely(remote));
            }
            log.error(
                    "the relay proxy could not evaluate flag {} either: {} {}",
                    key,
                    remote.getErrorCode(),
                    remote.getErrorMessage());
        } catch (Exception e) {
            // the OFREP client raises on responses it cannot read at all, and an exception escaping
            // here would replace the engine's error with one about the recovery attempt.
            log.error("the relay proxy could not be asked about flag {}", key, e);
        }
        return Optional.empty();
    }

    /**
     * markEvaluatedRemotely records in the flag metadata that the relay proxy produced this result.
     *
     * <p>The relay proxy's own metadata keys are kept: they describe the evaluation, which is the
     * proxy's, and only the marker is this provider's to add.</p>
     *
     * @param remote - answer the relay proxy gave
     * @param <T>    - type of the flag value
     * @return the same evaluation, carrying the marker
     */
    private static <T> ProviderEvaluation<T> markEvaluatedRemotely(final ProviderEvaluation<T> remote) {
        val metadata = new LinkedHashMap<String, Object>();
        if (remote.getFlagMetadata() != null) {
            metadata.putAll(remote.getFlagMetadata().asUnmodifiableMap());
        }
        metadata.put(Const.METADATA_EVALUATED_REMOTELY, true);
        remote.setFlagMetadata(MetadataUtil.convertFlagMetadata(metadata));
        return remote;
    }

    @FunctionalInterface
    private interface RemoteResolver<T> {
        ProviderEvaluation<T> resolve(IEvaluator remote, String key, T defaultValue, EvaluationContext ctx);
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
                    .flagMetadata(MetadataUtil.convertFlagMetadata(response.getMetadata()))
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
                .flatMap(tick -> Observable.fromCallable(() -> {
                            val configuration = this.api.retrieveFlagConfiguration(
                                    this.state.etag, options.getEvaluationFlagList());
                            emitRefreshSuccessEvent();
                            return configuration;
                        })
                        .onErrorResumeNext(e -> {
                            log.error("error while calling flag configuration API", e);
                            emitRefreshFailureEvent();
                            return Observable.<Optional<FlagConfigResponse>>empty();
                        }))
                // a 304 emits an empty Optional: drop it here so the refresh consumer below is
                // structurally unable to write state for a response that carries no configuration.
                .filter(Optional::isPresent)
                .map(Optional::get)
                .subscribeOn(Schedulers.io());

        return apiCallObservable.subscribe(
                response -> {
                    try {
                        applyFlagConfiguration(response);
                    } catch (Exception e) {
                        // an exception escaping the subscriber disposes it, which would stop polling
                        // for the lifetime of the provider rather than for this one refresh.
                        log.error("error while applying the flag configuration", e);
                    }
                },
                throwable -> log.error("flag configuration polling has stopped and will not resume", throwable));
    }

    /**
     * emitRefreshFailureEvent counts a failed refresh and marks the configuration stale once enough of
     * them have happened in a row.
     */
    private void emitRefreshFailureEvent() {
        if (this.consecutiveRefreshFailures.incrementAndGet() != Const.STALE_AFTER_CONSECUTIVE_FAILURES) {
            return;
        }

        log.warn(
                "{} consecutive failed refreshes, still serving the last known good configuration",
                Const.STALE_AFTER_CONSECUTIVE_FAILURES);
        this.staleAnnounced.set(true);
        try {
            this.emitter.accept(
                    ProviderEvent.PROVIDER_STALE,
                    ProviderEventDetails.builder()
                            .message("the flag configuration could not be refreshed "
                                    + Const.STALE_AFTER_CONSECUTIVE_FAILURES + " times in a row")
                            .build());
        } catch (Exception e) {
            log.error("error while emitting the {} event", ProviderEvent.PROVIDER_STALE, e);
        }
    }

    /**
     * emitRefreshSuccessEvent counts a refresh that worked and, if the configuration had been announced
     * as stale, announces that it no longer is.
     */
    private void emitRefreshSuccessEvent() {
        this.consecutiveRefreshFailures.set(0);
        if (!this.staleAnnounced.compareAndSet(true, false)) {
            return;
        }

        log.info("the flag configuration could be refreshed again");
        try {
            this.emitter.accept(
                    ProviderEvent.PROVIDER_READY,
                    ProviderEventDetails.builder()
                            .message("the flag configuration could be refreshed again")
                            .build());
        } catch (Exception e) {
            log.error("error while emitting the {} event", ProviderEvent.PROVIDER_READY, e);
        }
    }

    /**
     * applyFlagConfiguration replaces the configuration state with a newly retrieved one, unless the
     * response describes the configuration already held or an older one.
     *
     * <p>Both validators are optional: a relay proxy behind a cache or a reverse proxy may answer
     * without an {@code ETag} or with a {@code Last-Modified} this provider cannot parse, so a null
     * on either side means "cannot rule this response out" rather than a comparison.
     *
     * @param response - configuration returned by the last successful refresh
     */
    private void applyFlagConfiguration(final FlagConfigResponse response) {
        EvaluatorState current = this.state;
        if (response.getEtag() != null && response.getEtag().equals(current.etag)) {
            log.debug("flag configuration has not changed: {}", response);
            return;
        }

        if (response.getLastUpdated() != null
                && current.lastUpdate != null
                && response.getLastUpdated().before(current.lastUpdate)) {
            log.info("configuration received is older than the current one");
            return;
        }

        val flagChanges = findFlagConfigurationChanges(current.flags, response.getFlags());
        this.state = new EvaluatorState(
                response.getFlags(),
                response.getEvaluationContextEnrichment(),
                response.getEtag(),
                response.getLastUpdated());

        if (flagChanges.isEmpty()) {
            log.debug("flag configuration has not changed: {}", response);
            return;
        }

        log.info("flag configuration has changed");
        val changeDetails = ProviderEventDetails.builder()
                .flagsChanged(flagChanges)
                .message("flag configuration has changed")
                .build();
        this.emitter.accept(ProviderEvent.PROVIDER_CONFIGURATION_CHANGED, changeDetails);
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
