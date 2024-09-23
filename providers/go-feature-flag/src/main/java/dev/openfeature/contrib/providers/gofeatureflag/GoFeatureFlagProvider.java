package dev.openfeature.contrib.providers.gofeatureflag;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.openfeature.contrib.providers.gofeatureflag.bean.ConfigurationChange;
import dev.openfeature.contrib.providers.gofeatureflag.controller.CacheController;
import dev.openfeature.contrib.providers.gofeatureflag.controller.GoFeatureFlagController;
import dev.openfeature.contrib.providers.gofeatureflag.exception.ConfigurationChangeEndpointNotFound;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidTypeInCache;
import dev.openfeature.contrib.providers.gofeatureflag.hook.DataCollectorHook;
import dev.openfeature.contrib.providers.gofeatureflag.hook.DataCollectorHookOptions;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * GoFeatureFlagProvider is the JAVA provider implementation for the feature flag solution GO Feature Flag.
 */
@Slf4j
@SuppressWarnings({"checkstyle:NoFinalizer"})
public class GoFeatureFlagProvider extends EventProvider {
    public static final long DEFAULT_POLLING_CONFIG_FLAG_CHANGE_INTERVAL_MS = 2L * 60L * 1000L;
    protected static final String CACHED_REASON = Reason.CACHED.name();
    private static final String NAME = "GO Feature Flag Provider";

    private final GoFeatureFlagProviderOptions options;
    private final List<Hook> hooks = new ArrayList<>();
    private DataCollectorHook dataCollectorHook;
    private Disposable flagChangeDisposable;
    private GoFeatureFlagController gofeatureflagController;
    private CacheController cacheCtrl;

    /**
     * Constructor of the provider.
     *
     * @param options - options to configure the provider
     * @throws InvalidOptions - if options are invalid
     */
    public GoFeatureFlagProvider(GoFeatureFlagProviderOptions options) throws InvalidOptions {
        this.validateInputOptions(options);
        this.options = options;
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    @Override
    @SuppressFBWarnings({"EI_EXPOSE_REP"})
    public List<Hook> getProviderHooks() {
        return this.hooks;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(
            String key, Boolean defaultValue, EvaluationContext evaluationContext
    ) {
        return getEvaluation(key, defaultValue, evaluationContext, Boolean.class);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(
            String key, String defaultValue, EvaluationContext evaluationContext
    ) {
        return getEvaluation(key, defaultValue, evaluationContext, String.class);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(
            String key, Integer defaultValue, EvaluationContext evaluationContext
    ) {
        return getEvaluation(key, defaultValue, evaluationContext, Integer.class);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(
            String key, Double defaultValue, EvaluationContext evaluationContext
    ) {
        return getEvaluation(key, defaultValue, evaluationContext, Double.class);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(
            String key, Value defaultValue, EvaluationContext evaluationContext
    ) {
        return getEvaluation(key, defaultValue, evaluationContext, Value.class);
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        super.initialize(evaluationContext);
        this.gofeatureflagController = GoFeatureFlagController.builder().options(options).build();

        if (options.getEnableCache() == null || options.getEnableCache()) {
            this.cacheCtrl = CacheController.builder().options(options).build();

            if (!this.options.isDisableDataCollection()) {
                this.dataCollectorHook = new DataCollectorHook(DataCollectorHookOptions.builder()
                        .flushIntervalMs(options.getFlushIntervalMs())
                        .gofeatureflagController(this.gofeatureflagController)
                        .maxPendingEvents(options.getMaxPendingEvents())
                        .build());
                this.hooks.add(this.dataCollectorHook);
            }
            this.flagChangeDisposable =
                    this.startCheckFlagConfigurationChangesDaemon();
        }
        super.emitProviderReady(ProviderEventDetails.builder().message("Provider is ready to call the API").build());
        log.info("finishing initializing provider");
    }


    /**
     * startCheckFlagConfigurationChangesDaemon is a daemon that will check if the flag configuration has changed.
     *
     * @return Disposable - the subscription to the observable
     */
    @NotNull
    private Disposable startCheckFlagConfigurationChangesDaemon() {
        long pollingIntervalMs = options.getFlagChangePollingIntervalMs() != null
                ? options.getFlagChangePollingIntervalMs() : DEFAULT_POLLING_CONFIG_FLAG_CHANGE_INTERVAL_MS;

        PublishSubject<Object> stopSignal = PublishSubject.create();
        Observable<Long> intervalObservable = Observable.interval(pollingIntervalMs, TimeUnit.MILLISECONDS);
        Observable<ConfigurationChange> apiCallObservable = intervalObservable
                // as soon something is published in stopSignal, the interval will stop
                .takeUntil(stopSignal)
                .flatMap(tick -> Observable.fromCallable(() -> this.gofeatureflagController.configurationHasChanged())
                        .onErrorResumeNext(e -> {
                            log.error("error while calling flag change API", e);
                            if (e instanceof ConfigurationChangeEndpointNotFound) {
                                // emit an item to stop the interval to stop the loop
                                stopSignal.onNext(new Object());
                            }
                            return Observable.empty();
                        }))
                .subscribeOn(Schedulers.io());

        return apiCallObservable
                .subscribe(
                        response -> {
                            if (response == ConfigurationChange.FLAG_CONFIGURATION_UPDATED) {
                                log.info("clean up the cache because the flag configuration has changed");
                                this.cacheCtrl.invalidateAll();
                                super.emitProviderConfigurationChanged(ProviderEventDetails.builder()
                                        .message("GO Feature Flag Configuration changed, clearing the cache").build());
                            } else {
                                log.debug("flag configuration has not changed: {}", response);
                            }
                        },
                        throwable -> log.error("error while calling flag change API, error: {}", throwable.getMessage())
                );
    }

    /**
     * getEvaluation is the function resolving the flag, it will 1st check in the cache and if it is not available
     * will call the evaluation endpoint to get the value of the flag.
     *
     * @param key               - name of the feature flag
     * @param defaultValue      - value used if something is not working as expected
     * @param evaluationContext - EvaluationContext used for the request
     * @param expectedType      - type expected for the value
     * @param <T>               the type of your evaluation
     * @return a ProviderEvaluation that contains the open-feature response
     */
    @SuppressWarnings("unchecked")
    private <T> ProviderEvaluation<T> getEvaluation(
            String key, T defaultValue, EvaluationContext evaluationContext, Class<?> expectedType) {
        try {
            if (this.cacheCtrl == null) {
                return this.gofeatureflagController
                        .evaluateFlag(key, defaultValue, evaluationContext, expectedType)
                        .getProviderEvaluation();
            }

            ProviderEvaluation<?> cachedProviderEvaluation = this.cacheCtrl.getIfPresent(key, evaluationContext);
            if (cachedProviderEvaluation == null) {
                EvaluationResponse<T> proxyRes = this.gofeatureflagController.evaluateFlag(
                        key, defaultValue, evaluationContext, expectedType);

                if (Boolean.TRUE.equals(proxyRes.getCacheable())) {
                    this.cacheCtrl.put(key, evaluationContext, proxyRes.getProviderEvaluation());
                }
                return proxyRes.getProviderEvaluation();
            }
            cachedProviderEvaluation.setReason(CACHED_REASON);
            if (cachedProviderEvaluation.getValue().getClass() != expectedType) {
                throw new InvalidTypeInCache(expectedType, cachedProviderEvaluation.getValue().getClass());
            }
            return (ProviderEvaluation<T>) cachedProviderEvaluation;
        } catch (JsonProcessingException e) {
            log.error("Error building key for user", e);
            return this.gofeatureflagController
                    .evaluateFlag(key, defaultValue, evaluationContext, expectedType)
                    .getProviderEvaluation();
        } catch (InvalidTypeInCache e) {
            log.warn(e.getMessage(), e);
            return this.gofeatureflagController
                    .evaluateFlag(key, defaultValue, evaluationContext, expectedType)
                    .getProviderEvaluation();
        }
    }

    @Override
    public void shutdown() {
        log.debug("shutdown");
        if (this.dataCollectorHook != null) {
            this.dataCollectorHook.shutdown();
        }
        if (this.flagChangeDisposable != null) {
            this.flagChangeDisposable.dispose();
        }
        if (this.cacheCtrl != null) {
            this.cacheCtrl.invalidateAll();
        }
    }

    /**
     * validateInputOptions is validating the different options provided when creating the provider.
     *
     * @param options - Options used while creating the provider
     * @throws InvalidOptions  - if no options are provided
     * @throws InvalidEndpoint - if the endpoint provided is not valid
     */
    private void validateInputOptions(GoFeatureFlagProviderOptions options) throws InvalidOptions {
        if (options == null) {
            throw new InvalidOptions("No options provided");
        }

        if (options.getEndpoint() == null || options.getEndpoint().isEmpty()) {
            throw new InvalidEndpoint("endpoint is a mandatory field when initializing the provider");
        }
    }

    /**
     * DO NOT REMOVE, spotbugs: CT_CONSTRUCTOR_THROW.
     *
     * @deprecated (Kept for compatibility with OpenFeatureAPI)
     */
    @Deprecated
    protected final void finalize() {
        // DO NOT REMOVE, spotbugs: CT_CONSTRUCTOR_THROW
    }
}
