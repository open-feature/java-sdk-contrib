package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.process.InProcessResolver;
import dev.openfeature.contrib.providers.flagd.resolver.rpc.RpcResolver;
import dev.openfeature.contrib.providers.flagd.resolver.rpc.cache.Cache;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Hook;
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
    private final List<Hook> hooks = new ArrayList<>();
    private final FlagdProviderSyncResources syncResources = new FlagdProviderSyncResources();

    /**
     * An executor service responsible for emitting
     * {@link ProviderEvent#PROVIDER_ERROR} after the provider went
     * {@link ProviderEvent#PROVIDER_STALE} for {@link #gracePeriod} seconds.
     */
    private final ScheduledExecutorService errorExecutor;

    /**
     * A scheduled task for emitting {@link ProviderEvent#PROVIDER_ERROR}.
     */
    private ScheduledFuture<?> errorTask;

    /**
     * The grace period in milliseconds to wait after
     * {@link ProviderEvent#PROVIDER_STALE} before emitting a
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
            case Config.RESOLVER_FILE:
            case Config.RESOLVER_IN_PROCESS:
                this.flagResolver = new InProcessResolver(options, this::onProviderEvent);
                break;
            case Config.RESOLVER_RPC:
                this.flagResolver = new RpcResolver(
                        options, new Cache(options.getCacheType(), options.getMaxCacheSize()), this::onProviderEvent);
                break;
            default:
                throw new IllegalStateException(
                        String.format("Requested unsupported resolver type of %s", options.getResolverType()));
        }
        hooks.add(new SyncMetadataHook(this::getEnrichedContext));
        contextEnricher = options.getContextEnricher();
        errorExecutor = Executors.newSingleThreadScheduledExecutor(new FlagdThreadFactory("flagd-provider-thread"));
        gracePeriod = options.getRetryGracePeriod();
        deadline = options.getDeadline();
    }

    /**
     * Internal constructor for test cases.
     * DO NOT MAKE PUBLIC
     */
    FlagdProvider(Resolver resolver, boolean initialized) {
        this.flagResolver = resolver;
        deadline = Config.DEFAULT_DEADLINE;
        gracePeriod = Config.DEFAULT_STREAM_RETRY_GRACE_PERIOD;
        hooks.add(new SyncMetadataHook(this::getEnrichedContext));
        errorExecutor = Executors.newSingleThreadScheduledExecutor(new FlagdThreadFactory("flagd-provider-thread"));
        if (initialized) {
            this.syncResources.initialize();
        }
    }

    @Override
    public List<Hook> getProviderHooks() {
        return Collections.unmodifiableList(hooks);
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        synchronized (syncResources) {
            if (syncResources.isInitialized()) {
                return;
            }

            flagResolver.init();
            // block till ready - this works with deadline fine for rpc, but with in_process
            // we also need to take parsing into the equation
            // TODO: evaluate where we are losing time, so we can remove this magic number -
            syncResources.waitForInitialization(this.deadline * 2);
        }
    }

    @Override
    public void shutdown() {
        synchronized (syncResources) {
            try {
                if (!syncResources.isInitialized() || syncResources.isShutDown()) {
                    return;
                }

                this.flagResolver.shutdown();
                if (errorExecutor != null) {
                    errorExecutor.shutdownNow();
                    errorExecutor.awaitTermination(deadline, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                log.error("Error during shutdown {}", FLAGD_PROVIDER, e);
            } finally {
                syncResources.shutdown();
            }
        }
    }

    @Override
    public Metadata getMetadata() {
        return () -> FLAGD_PROVIDER;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return flagResolver.booleanEvaluation(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return flagResolver.stringEvaluation(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return flagResolver.doubleEvaluation(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return flagResolver.integerEvaluation(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return flagResolver.objectEvaluation(key, defaultValue, ctx);
    }

    /**
     * The updated context mixed into all evaluations based on the sync-metadata.
     *
     * @return context
     */
    EvaluationContext getEnrichedContext() {
        return syncResources.getEnrichedContext();
    }

    @SuppressWarnings("checkstyle:fallthrough")
    private void onProviderEvent(
            ProviderEvent providerEvent, ProviderEventDetails providerEventDetails, Structure syncMetadata) {
        log.debug("FlagdProviderEvent event {} ", providerEvent);
        synchronized (syncResources) {
            /*
             * We only use Error and Ready as previous states.
             * As error will first be emitted as Stale, and only turns after a while into an
             * emitted Error.
             * Ready is needed, as the InProcessResolver does not have a dedicated ready
             * event, hence we need to
             * forward a configuration changed to the ready, if we are not in the ready
             * state.
             */
            switch (providerEvent) {
                case PROVIDER_CONFIGURATION_CHANGED:
                    if (syncResources.getPreviousEvent() == ProviderEvent.PROVIDER_READY) {
                        emit(providerEvent, providerEventDetails);
                        break;
                    }
                // intentional fall through
                case PROVIDER_READY:
                    /*
                     * Sync metadata is used to enrich the context, and is immutable in flagd,
                     * so we only need it to be fetched once at READY.
                     */
                    if (syncMetadata != null) {
                        syncResources.setEnrichedContext(contextEnricher.apply(syncMetadata));
                    }
                    onReady();
                    syncResources.setPreviousEvent(ProviderEvent.PROVIDER_READY);
                    break;
                case PROVIDER_ERROR:
                    if (providerEventDetails != null
                            && providerEventDetails.getErrorCode() == ErrorCode.PROVIDER_FATAL) {
                        onFatal();
                        break;
                    }

                    if (syncResources.getPreviousEvent() != ProviderEvent.PROVIDER_ERROR) {
                        onError();
                        syncResources.setPreviousEvent(ProviderEvent.PROVIDER_ERROR);
                    }
                    break;
                default:
                    log.warn("Unknown event {}", providerEvent);
            }
        }
    }

    private void onReady() {
        if (syncResources.initialize()) {
            log.info("Initialized FlagdProvider");
        }
        if (errorTask != null && !errorTask.isCancelled()) {
            errorTask.cancel(false);
            log.debug("Reconnection task cancelled as connection became READY.");
        }
        this.emitProviderReady(
                ProviderEventDetails.builder().message("connected to flagd").build());
    }

    private void onError() {
        log.debug(
                "Stream error. Emitting STALE, scheduling ERROR, and waiting {}s for connection to become available.",
                gracePeriod);
        this.emitProviderStale(ProviderEventDetails.builder()
                .message("there has been an error")
                .build());

        if (errorTask != null && !errorTask.isCancelled()) {
            errorTask.cancel(false);
        }

        if (!errorExecutor.isShutdown()) {
            errorTask = errorExecutor.schedule(
                    () -> {
                        if (syncResources.getPreviousEvent() == ProviderEvent.PROVIDER_ERROR) {
                            log.error(
                                    "Provider did not reconnect successfully within {}s. Emitting ERROR event...",
                                    gracePeriod);
                            flagResolver.onError();
                            this.emitProviderError(ProviderEventDetails.builder()
                                    .message("there has been an error")
                                    .build());
                        }
                    },
                    gracePeriod,
                    TimeUnit.SECONDS);
        }
    }

    private void onFatal() {
        if (errorTask != null && !errorTask.isCancelled()) {
            errorTask.cancel(false);
        }
        this.syncResources.setFatal(true);

        this.emitProviderError(ProviderEventDetails.builder()
                .errorCode(ErrorCode.PROVIDER_FATAL)
                .build());

        shutdown();
    }
}
