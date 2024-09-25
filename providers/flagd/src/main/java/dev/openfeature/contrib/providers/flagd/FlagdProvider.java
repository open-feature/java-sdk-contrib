package dev.openfeature.contrib.providers.flagd;

import java.util.Collections;
import java.util.Map;

import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.common.ConnectionEvent;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.GrpcResolver;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.Cache;
import dev.openfeature.contrib.providers.flagd.resolver.process.InProcessResolver;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenFeature provider for flagd.
 */
@Slf4j
@SuppressWarnings({ "PMD.TooManyStaticImports", "checkstyle:NoFinalizer" })
public class FlagdProvider extends EventProvider {
    private static final String FLAGD_PROVIDER = "flagD Provider";
    private final Resolver flagResolver;
    private volatile boolean initialized = false;
    private volatile boolean connected = false;
    private volatile Map<String, Object> syncMetadata = Collections.emptyMap();

    private EvaluationContext evaluationContext;

    protected final void finalize() {
        // DO NOT REMOVE, spotbugs: CT_CONSTRUCTOR_THROW
    }

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
        switch (options.getResolverType().asString()) {
            case Config.RESOLVER_IN_PROCESS:
                this.flagResolver = new InProcessResolver(options, this::isConnected,
                        this::onConnectionEvent);
                break;
            case Config.RESOLVER_RPC:
                this.flagResolver = new GrpcResolver(options,
                        new Cache(options.getCacheType(), options.getMaxCacheSize()),
                        this::isConnected,
                        this::onConnectionEvent);
                break;
            default:
                throw new IllegalStateException(
                        String.format("Requested unsupported resolver type of %s", options.getResolverType()));
        }
    }

    @Override
    public synchronized void initialize(EvaluationContext evaluationContext) throws Exception {
        if (this.initialized) {
            return;
        }

        this.evaluationContext = evaluationContext;
        this.flagResolver.init();
        this.initialized = true;
    }

    @Override
    public synchronized void shutdown() {
        if (!this.initialized) {
            return;
        }

        try {
            this.flagResolver.shutdown();
        } catch (Exception e) {
            log.error("Error during shutdown {}", FLAGD_PROVIDER, e);
        } finally {
            this.initialized = false;
        }
    }

    @Override
    public Metadata getMetadata() {
        return () -> FLAGD_PROVIDER;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return this.flagResolver.booleanEvaluation(key, defaultValue, mergeContext(ctx));
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return this.flagResolver.stringEvaluation(key, defaultValue, mergeContext(ctx));
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return this.flagResolver.doubleEvaluation(key, defaultValue, mergeContext(ctx));
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return this.flagResolver.integerEvaluation(key, defaultValue, mergeContext(ctx));
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return this.flagResolver.objectEvaluation(key, defaultValue, mergeContext(ctx));
    }

    /**
     * An unmodifiable view of an object map representing the latest result of the
     * SyncMetadata.
     * Set on initial connection and updated with every reconnection.
     * see:
     * https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1#flagd.sync.v1.FlagSyncService.GetMetadata
     * 
     * @return Object map representing sync metadata
     */
    protected Map<String, Object> getSyncMetadata() {
        return Collections.unmodifiableMap(syncMetadata);
    }

    private EvaluationContext mergeContext(final EvaluationContext clientCallCtx) {
        if (this.evaluationContext != null) {
            return evaluationContext.merge(clientCallCtx);
        }

        return clientCallCtx;
    }

    private boolean isConnected() {
        return this.connected;
    }

    private void onConnectionEvent(ConnectionEvent connectionEvent) {
        boolean previous = connected;
        boolean current = connected = connectionEvent.isConnected();
        syncMetadata = connectionEvent.getSyncMetadata();

        // configuration changed
        if (initialized && previous && current) {
            log.debug("Configuration changed");
            ProviderEventDetails details = ProviderEventDetails.builder()
                    .flagsChanged(connectionEvent.getFlagsChanged())
                    .message("configuration changed").build();
            this.emitProviderConfigurationChanged(details);
            return;
        }
        // there was an error
        if (initialized && previous && !current) {
            log.debug("There has been an error");
            ProviderEventDetails details = ProviderEventDetails.builder().message("there has been an error").build();
            this.emitProviderError(details);
            return;
        }
        // we recovered from an error
        if (initialized && !previous && current) {
            log.debug("Recovered from error");
            ProviderEventDetails details = ProviderEventDetails.builder().message("recovered from error").build();
            this.emitProviderReady(details);
            this.emitProviderConfigurationChanged(details);
        }
    }
}
