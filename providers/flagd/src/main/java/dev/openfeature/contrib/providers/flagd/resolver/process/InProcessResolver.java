package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.FlagStore;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.Storage;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageStateChange;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.file.FileQueueSource;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.SyncStreamQueueSource;
import dev.openfeature.contrib.tools.flagd.api.Evaluator;
import dev.openfeature.contrib.tools.flagd.core.FlagdCore;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.internal.TriConsumer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves flag values using
 * https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1.
 * Flags are evaluated locally.
 */
@Slf4j
public class InProcessResolver implements Resolver {

    static final String STATE_WATCHER_THREAD_NAME = "InProcessResolver.stateWatcher";
    private final Storage flagStore;
    private final TriConsumer<ProviderEvent, ProviderEventDetails, Structure> onConnectionEvent;
    private final Evaluator evaluator;
    private final QueueSource queueSource;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicReference<Thread> stateWatcher = new AtomicReference<>();

    /**
     * Resolves flag values using
     * https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1.
     * Flags are evaluated locally.
     *
     * @param options           flagd options
     * @param onConnectionEvent lambda which handles changes in the
     *                          connection/stream
     */
    public InProcessResolver(
            FlagdOptions options, TriConsumer<ProviderEvent, ProviderEventDetails, Structure> onConnectionEvent) {
        this.queueSource = getQueueSource(options);
        Evaluator flagdCore = new FlagdCore();
        this.evaluator = flagdCore;
        this.flagStore = new FlagStore(queueSource, flagdCore);
        this.onConnectionEvent = onConnectionEvent;
    }

    /**
     * Initialize in-process resolver.
     */
    public void init() throws Exception {
        flagStore.init();
        final Thread stateWatcher = new Thread(this::stateWatcher, STATE_WATCHER_THREAD_NAME);
        stateWatcher.setDaemon(true);
        this.stateWatcher.set(stateWatcher);
        stateWatcher.start();
    }

    private void stateWatcher() {
        try {
            while (!shutdown.get()) {
                final StorageStateChange storageStateChange =
                        flagStore.getStateQueue().take();
                switch (storageStateChange.getStorageState()) {
                    case OK:
                        log.debug("onConnectionEvent.accept ProviderEvent.PROVIDER_CONFIGURATION_CHANGED");

                        var eventDetails = ProviderEventDetails.builder()
                                .flagsChanged(storageStateChange.getChangedFlagsKeys())
                                .message("configuration changed")
                                .build();

                        onConnectionEvent.accept(
                                ProviderEvent.PROVIDER_CONFIGURATION_CHANGED,
                                eventDetails,
                                storageStateChange.getSyncMetadata());

                        log.debug("post onConnectionEvent.accept ProviderEvent.PROVIDER_CONFIGURATION_CHANGED");
                        break;
                    case STALE:
                        onConnectionEvent.accept(ProviderEvent.PROVIDER_ERROR, null, null);
                        break;
                    case ERROR:
                        onConnectionEvent.accept(
                                ProviderEvent.PROVIDER_ERROR,
                                ProviderEventDetails.builder()
                                        .errorCode(ErrorCode.PROVIDER_FATAL)
                                        .build(),
                                null);
                        break;
                    default:
                        log.warn(String.format(
                                "Storage emitted unhandled status: %s", storageStateChange.getStorageState()));
                }
            }
        } catch (InterruptedException e) {
            log.debug("Storage state watcher interrupted, most likely shutdown was invoked", e);
        }
    }

    /**
     * Called when the provider enters error state after grace period.
     * Attempts to reinitialize the sync connector if enabled.
     */
    @Override
    public void onError() {
        if (queueSource instanceof SyncStreamQueueSource) {
            SyncStreamQueueSource syncConnector = (SyncStreamQueueSource) queueSource;
            // only reinitialize if option is enabled
            syncConnector.reinitializeChannelComponents();
        }
    }

    /**
     * Shutdown in-process resolver.
     *
     * @throws InterruptedException if stream can't be closed within deadline.
     */
    public void shutdown() throws InterruptedException {
        if (!shutdown.compareAndSet(false, true)) {
            log.debug("Shutdown already in progress or completed");
            return;
        }
        flagStore.shutdown();
        stateWatcher.getAndUpdate(existing -> {
            if (existing != null && existing.isAlive()) {
                existing.interrupt();
            }
            return null;
        });
    }

    /**
     * Resolve a boolean flag.
     */
    public ProviderEvaluation<Boolean> booleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return evaluator.resolveBooleanValue(key, ctx);
    }

    /**
     * Resolve a string flag.
     */
    public ProviderEvaluation<String> stringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return evaluator.resolveStringValue(key, ctx);
    }

    /**
     * Resolve a double flag.
     */
    public ProviderEvaluation<Double> doubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return evaluator.resolveDoubleValue(key, ctx);
    }

    /**
     * Resolve an integer flag.
     */
    public ProviderEvaluation<Integer> integerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return evaluator.resolveIntegerValue(key, ctx);
    }

    /**
     * Resolve an object flag.
     */
    public ProviderEvaluation<Value> objectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return evaluator.resolveObjectValue(key, ctx);
    }

    static QueueSource getQueueSource(final FlagdOptions options) {
        if (options.getCustomConnector() != null) {
            return options.getCustomConnector();
        }
        return options.getOfflineFlagSourcePath() != null
                        && !options.getOfflineFlagSourcePath().isEmpty()
                ? new FileQueueSource(options.getOfflineFlagSourcePath(), options.getOfflinePollIntervalMs())
                : new SyncStreamQueueSource(options);
    }
}
