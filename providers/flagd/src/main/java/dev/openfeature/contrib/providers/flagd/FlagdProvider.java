package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.contrib.providers.flagd.grpc.GrpcConnector;
import dev.openfeature.contrib.providers.flagd.strategy.ResolveFactory;
import dev.openfeature.contrib.providers.flagd.strategy.ResolveStrategy;
import dev.openfeature.flagd.grpc.Schema.ResolveBooleanRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveFloatRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveIntRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveObjectRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveStringRequest;
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

    private final FlagdCache cache;
    private final ResolveStrategy strategy;
    private final GrpcConnector grpc;
    private final FlagResolution flagResolver;


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
        this.strategy = ResolveFactory.getStrategy(options);
        this.cache = new FlagdCache(options.getCacheType(), options.getMaxCacheSize());
        this.grpc = new GrpcConnector(options, this.cache, this::setState);
        this.flagResolver = new FlagResolution(this.cache, this.strategy, this::getState);
    }

    FlagdProvider(ResolveStrategy strategy, FlagdCache cache, GrpcConnector grpc, FlagResolution resolution) {
        this.strategy = strategy;
        this.cache = cache;
        this.grpc = grpc;
        this.flagResolver = resolution;
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws RuntimeException {
        this.grpc.initialize(evaluationContext);
    }

    @Override
    public void shutdown() {
        try {
            this.grpc.shutdown();
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
        // we are connected, reset the gRPC retry logic
        if (ProviderState.READY.equals(newState)) {
            this.grpc.resetRetryConnection();
        }
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


    @Override
    public Metadata getMetadata() {
        return () -> FLAGD_PROVIDER;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue,
                                                            EvaluationContext ctx) {

        ResolveBooleanRequest request = ResolveBooleanRequest.newBuilder().buildPartial();

        return this.flagResolver.resolve(key, ctx, request,
                this.grpc.getResolver()::resolveBoolean, null);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue,
                                                          EvaluationContext ctx) {
        ResolveStringRequest request = ResolveStringRequest.newBuilder().buildPartial();

        return this.flagResolver.resolve(key, ctx, request,
                this.grpc.getResolver()::resolveString, null);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue,
                                                          EvaluationContext ctx) {
        ResolveFloatRequest request = ResolveFloatRequest.newBuilder().buildPartial();

        return this.flagResolver.resolve(key, ctx, request,
                this.grpc.getResolver()::resolveFloat, null);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue,
                                                            EvaluationContext ctx) {

        ResolveIntRequest request = ResolveIntRequest.newBuilder().buildPartial();

        return this.flagResolver.resolve(key, ctx, request,
                this.grpc.getResolver()::resolveInt,
                (Object value) -> ((Long) value).intValue());
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue,
                                                         EvaluationContext ctx) {

        ResolveObjectRequest request = ResolveObjectRequest.newBuilder().buildPartial();

        return this.flagResolver.resolve(key, ctx, request,
                this.grpc.getResolver()::resolveObject,
                (Object value) -> this.flagResolver.convertObjectResponse((com.google.protobuf.Struct) value));
    }

}
