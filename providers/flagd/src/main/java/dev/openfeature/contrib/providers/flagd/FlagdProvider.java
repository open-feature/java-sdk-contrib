package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.contrib.providers.flagd.cache.CacheFactory;
import dev.openfeature.contrib.providers.flagd.grpc.GrpcResolution;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * OpenFeature provider for flagd.
 */
@Slf4j
@SuppressWarnings("PMD.TooManyStaticImports")
public class FlagdProvider extends EventProvider implements FeatureProvider {
    private static final String FLAGD_PROVIDER = "flagD Provider";

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Resolver flagResolver;

    private ProviderState state = ProviderState.NOT_READY;

    /**
     * Create a new FlagdProvider instance with default options.
     */
    public FlagdProvider() {
        this(FlagdOptions.builder().build());
    }

    /**
     * Create a new FlagdProvider instance with customized options.
     *
     * @param options {@link FlagdOptions} with
     */
    public FlagdProvider(final FlagdOptions options) {
        this.flagResolver = new GrpcResolution(options, CacheFactory.getCache(options), this::getState, this::setState);
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        this.flagResolver.init();
    }

    @Override
    public void shutdown() {
        try {
            this.flagResolver.shutdown();
        } catch (Exception e) {
            log.error("Error during shutdown {}", FLAGD_PROVIDER, e);
        }
    }

    @Override
    public ProviderState getState() {
        Lock l = this.lock.readLock();
        try {
            l.lock();
            return this.state;
        } finally {
            l.unlock();
        }
    }


    @Override
    public Metadata getMetadata() {
        return () -> FLAGD_PROVIDER;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return this.flagResolver.booleanEvaluation(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return this.flagResolver.stringEvaluation(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return this.flagResolver.doubleEvaluation(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return this.flagResolver.integerEvaluation(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return this.flagResolver.objectEvaluation(key, defaultValue, ctx);
    }

    private void setState(ProviderState newState) {
        ProviderState oldState;
        Lock l = this.lock.writeLock();
        try {
            l.lock();
            oldState = this.state;
            this.state = newState;
        } finally {
            l.unlock();
        }
        this.handleStateTransition(oldState, newState);
    }

    private void handleStateTransition(ProviderState oldState, ProviderState newState) {
        // we got initialized
        if (ProviderState.NOT_READY.equals(oldState) && ProviderState.READY.equals(newState)) {
            // nothing to do, the SDK emits the events
            log.debug("Init completed");
            return;
        }
        // configuration changed
        if (ProviderState.READY.equals(oldState) && ProviderState.READY.equals(newState)) {
            log.debug("Configuration changed");
            ProviderEventDetails details = ProviderEventDetails.builder().message("configuration changed").build();
            this.emitProviderConfigurationChanged(details);
            return;
        }
        // there was an error
        if (ProviderState.READY.equals(oldState) && ProviderState.ERROR.equals(newState)) {
            log.debug("There has been an error");
            ProviderEventDetails details = ProviderEventDetails.builder().message("there has been an error").build();
            this.emitProviderError(details);
            return;
        }
        // we recover from an error
        if (ProviderState.ERROR.equals(oldState) && ProviderState.READY.equals(newState)) {
            log.debug("Recovered from error");
            ProviderEventDetails details = ProviderEventDetails.builder().message("recovered from error").build();
            this.emitProviderReady(details);
            this.emitProviderConfigurationChanged(details);
        }
    }
}
