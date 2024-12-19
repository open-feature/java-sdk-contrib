package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.common.ConnectionEvent;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.GrpcResolver;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.Cache;
import dev.openfeature.contrib.providers.flagd.resolver.process.InProcessResolver;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * OpenFeature provider for flagd.
 */
@Slf4j
@SuppressWarnings({"PMD.TooManyStaticImports", "checkstyle:NoFinalizer"})
public class FlagdProvider extends EventProvider {
    private Function<Structure, EvaluationContext> contextEnricher;
    private static final String FLAGD_PROVIDER = "flagd";
    private final Resolver flagResolver;
    private volatile boolean isInitialized = false;
    private volatile boolean connected = false;
    private volatile Structure syncMetadata = new ImmutableStructure();
    private volatile EvaluationContext enrichedContext = new ImmutableContext();
    private final List<Hook> hooks = new ArrayList<>();

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
                        this::onConnectionEvent);
                break;
            default:
                throw new IllegalStateException(
                        String.format("Requested unsupported resolver type of %s", options.getResolverType()));
        }
        hooks.add(new SyncMetadataHook(this::getEnrichedContext));
        contextEnricher = options.getContextEnricher();
    }

    @Override
    public List<Hook> getProviderHooks() {
        return Collections.unmodifiableList(hooks);
    }

    @Override
    public synchronized void initialize(EvaluationContext evaluationContext) throws Exception {
        if (this.isInitialized) {
            return;
        }

        this.flagResolver.init();
        this.isInitialized = true;
    }

    @Override
    public synchronized void shutdown() {
        if (!this.isInitialized) {
            return;
        }

        try {
            this.flagResolver.shutdown();
        } catch (Exception e) {
            log.error("Error during shutdown {}", FLAGD_PROVIDER, e);
        } finally {
            this.isInitialized = false;
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

    /**
     * An unmodifiable view of a Structure representing the latest result of the
     * SyncMetadata.
     * Set on initial connection and updated with every reconnection.
     * see:
     * https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1#flagd.sync.v1.FlagSyncService.GetMetadata
     *
     * @return Object map representing sync metadata
     */
    protected Structure getSyncMetadata() {
        return new ImmutableStructure(syncMetadata.asMap());
    }

    /**
     * The updated context mixed into all evaluations based on the sync-metadata.
     *
     * @return context
     */
    EvaluationContext getEnrichedContext() {
        return enrichedContext;
    }

    private boolean isConnected() {
        return this.connected;
    }

    private void onConnectionEvent(ConnectionEvent connectionEvent) {
        final boolean wasConnected = connected;
        final boolean isConnected = connected = connectionEvent.isConnected();

        syncMetadata = connectionEvent.getSyncMetadata();
        enrichedContext = contextEnricher.apply(connectionEvent.getSyncMetadata());

        if (!isInitialized) {
            return;
        }

        if (!wasConnected && isConnected) {
            ProviderEventDetails details = ProviderEventDetails.builder()
                    .flagsChanged(connectionEvent.getFlagsChanged())
                    .message("connected to flagd")
                    .build();
            this.emitProviderReady(details);
            return;
        }

        if (wasConnected && isConnected) {
            ProviderEventDetails details = ProviderEventDetails.builder()
                    .flagsChanged(connectionEvent.getFlagsChanged())
                    .message("configuration changed")
                    .build();
            this.emitProviderConfigurationChanged(details);
            return;
        }

        if (connectionEvent.isStale()) {
            this.emitProviderStale(ProviderEventDetails.builder().message("there has been an error").build());
        } else {
            this.emitProviderError(ProviderEventDetails.builder().message("there has been an error").build());
        }
    }
}



