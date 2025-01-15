package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.common.FlagdProviderEvent;
import dev.openfeature.contrib.providers.flagd.resolver.common.Util;
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
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenFeature provider for flagd.
 */
@Slf4j
@SuppressWarnings({"PMD.TooManyStaticImports", "checkstyle:NoFinalizer"})
public class FlagdProvider extends EventProvider {
    private Function<Structure, EvaluationContext> contextEnricher;
    private static final String FLAGD_PROVIDER = "flagd";
    private final Resolver flagResolver;
    private volatile boolean initialized = false;
    private volatile Structure syncMetadata = new ImmutableStructure();
    private volatile EvaluationContext enrichedContext = new ImmutableContext();
    private final List<Hook> hooks = new ArrayList<>();
    private volatile ProviderEvent previousEvent = null;

    /**
     * An executor service responsible for emitting {@link ProviderEvent#PROVIDER_ERROR} after the provider went
     * {@link ProviderEvent#PROVIDER_STALE} for {@link #gracePeriod} seconds.
     */
    private final ScheduledExecutorService errorExecutor;

    /**
     * A scheduled task for emitting {@link ProviderEvent#PROVIDER_ERROR}.
     */
    private ScheduledFuture<?> reconnectTask;

    /**
     * The grace period in milliseconds to wait after {@link ProviderEvent#PROVIDER_STALE} before emitting a
     * {@link ProviderEvent#PROVIDER_ERROR}.
     */
    private final long gracePeriod;
    /**
     * The deadline in milliseconds for GRPC operations.
     */
    private final long deadline;

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
                this.flagResolver = new InProcessResolver(options, this::onProviderEvent);
                break;
            case Config.RESOLVER_RPC:
                this.flagResolver = new GrpcResolver(
                        options, new Cache(options.getCacheType(), options.getMaxCacheSize()), this::onProviderEvent);
                break;
            default:
                throw new IllegalStateException(
                        String.format("Requested unsupported resolver type of %s", options.getResolverType()));
        }
        hooks.add(new SyncMetadataHook(this::getEnrichedContext));
        contextEnricher = options.getContextEnricher();
        this.errorExecutor = Executors.newSingleThreadScheduledExecutor();
        this.gracePeriod = options.getRetryGracePeriod();
        this.deadline = options.getDeadline();
    }

    @Override
    public List<Hook> getProviderHooks() {
        return Collections.unmodifiableList(hooks);
    }

    @Override
    public synchronized void initialize(EvaluationContext evaluationContext) throws Exception {
        if (this.initialized) {
            return;
        }

        this.flagResolver.init();
        // block till ready - this works with deadline fine for rpc, but with in_process we also need to take parsing
        // into the equation
        // TODO: evaluate where we are losing time, so we can remove this magic number - follow up
        Util.busyWaitAndCheck(this.deadline + 500, () -> initialized);
    }

    @Override
    public synchronized void shutdown() {
        if (!this.initialized) {
            return;
        }
        try {
            this.flagResolver.shutdown();
            if (errorExecutor != null) {
                errorExecutor.shutdownNow();
                errorExecutor.awaitTermination(deadline, TimeUnit.MILLISECONDS);
            }
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

    @SuppressWarnings("checkstyle:fallthrough")
    private void onProviderEvent(FlagdProviderEvent flagdProviderEvent) {

        syncMetadata = flagdProviderEvent.getSyncMetadata();
        if (flagdProviderEvent.getSyncMetadata() != null) {
            enrichedContext = contextEnricher.apply(flagdProviderEvent.getSyncMetadata());
        }

        /*
        We only use Error and Ready as previous states.
        As error will first be emitted as Stale, and only turns after a while into an emitted Error.
        Ready is needed, as the InProcessResolver does not have a dedicated ready event, hence we need to
        forward a configuration changed to the ready, if we are not in the ready state.
         */
        switch (flagdProviderEvent.getEvent()) {
            case PROVIDER_CONFIGURATION_CHANGED:
                if (previousEvent == ProviderEvent.PROVIDER_READY) {
                    onConfigurationChanged(flagdProviderEvent);
                    break;
                }
                // intentional fall through, a not-ready change will trigger a ready.
            case PROVIDER_READY:
                onReady();
                previousEvent = ProviderEvent.PROVIDER_READY;
                break;

            case PROVIDER_ERROR:
                if (previousEvent != ProviderEvent.PROVIDER_ERROR) {
                    onError();
                }
                previousEvent = ProviderEvent.PROVIDER_ERROR;
                break;
            default:
                log.info("Unknown event {}", flagdProviderEvent.getEvent());
        }
    }

    private void onConfigurationChanged(FlagdProviderEvent flagdProviderEvent) {
        this.emitProviderConfigurationChanged(ProviderEventDetails.builder()
                .flagsChanged(flagdProviderEvent.getFlagsChanged())
                .message("configuration changed")
                .build());
    }

    private void onReady() {
        if (!initialized) {
            initialized = true;
            log.info("initialized FlagdProvider");
        }
        if (reconnectTask != null && !reconnectTask.isCancelled()) {
            reconnectTask.cancel(false);
            log.debug("Reconnection task cancelled as connection became READY.");
        }
        this.emitProviderReady(
                ProviderEventDetails.builder().message("connected to flagd").build());
    }

    private void onError() {
        log.info("Connection lost. Emit STALE event...");
        log.debug("Waiting {}s for connection to become available...", gracePeriod);
        this.emitProviderStale(ProviderEventDetails.builder()
                .message("there has been an error")
                .build());

        if (reconnectTask != null && !reconnectTask.isCancelled()) {
            reconnectTask.cancel(false);
        }

        if (!errorExecutor.isShutdown()) {
            reconnectTask = errorExecutor.schedule(
                    () -> {
                        log.debug(
                                "Provider did not reconnect successfully within {}s. Emit ERROR event...", gracePeriod);
                        flagResolver.onError();
                        this.emitProviderError(ProviderEventDetails.builder()
                                .message("there has been an error")
                                .build());
                    },
                    gracePeriod,
                    TimeUnit.SECONDS);
        }
    }
}
