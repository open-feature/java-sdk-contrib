package dev.openfeature.contrib.providers.flagd.resolver.process;

import static dev.openfeature.contrib.providers.flagd.resolver.common.Convert.convertProtobufMapToStructure;

import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.common.FlagdProviderEvent;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.file.FileQueueSource;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.SyncStreamQueueSource;
import dev.openfeature.flagd.evaluator.EvaluationResult;
import dev.openfeature.flagd.evaluator.EvaluatorException;
import dev.openfeature.flagd.evaluator.FlagEvaluator;
import dev.openfeature.flagd.evaluator.UpdateStateResult;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves flag values using
 * https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1.
 * Flags are evaluated locally.
 */
@Slf4j
public class InProcessWasmResolver implements Resolver {

    private final Consumer<FlagdProviderEvent> onConnectionEvent;
    private final QueueSource connector;
    private Thread stateWatcher;
    private FlagEvaluator evaluator;

    /**
     * Resolves flag values using
     * https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1.
     * Flags are evaluated locally.
     *
     * @param options           flagd options
     * @param onConnectionEvent lambda which handles changes in the
     *                          connection/stream
     */
    public InProcessWasmResolver(FlagdOptions options, Consumer<FlagdProviderEvent> onConnectionEvent) {
        this.evaluator = new FlagEvaluator(FlagEvaluator.ValidationMode.PERMISSIVE);

        this.onConnectionEvent = onConnectionEvent;
        this.connector = getConnector(options);
    }

    /**
     * Initialize in-process resolver.
     */
    public void init() throws Exception {

        connector.init();
        this.stateWatcher = new Thread(() -> {
            var streamPayloads = connector.getStreamQueue();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final QueuePayload payload = streamPayloads.take();
                    switch (payload.getType()) {
                        case DATA:
                            try {
                                UpdateStateResult updateStateResult = evaluator.updateState(payload.getFlagData());

                                Structure syncContext = parseSyncContext(payload.getSyncContext());
                                if (updateStateResult.isSuccess()) {
                                    onConnectionEvent.accept(new FlagdProviderEvent(
                                            ProviderEvent.PROVIDER_CONFIGURATION_CHANGED,
                                            updateStateResult.getChangedFlags(),
                                            syncContext));
                                }
                            } catch (EvaluatorException e) {
                                log.error("Error updating state from WASM evaluator", e);
                            }


                            break;
                        case ERROR:
                            log.error("Received error payload from connector");
                            break;
                        default:
                            log.warn(String.format("Payload with unknown type: %s", payload.getType()));
                    }
                } catch (InterruptedException e) {
                    log.debug("Storage state watcher interrupted", e);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        this.stateWatcher.setDaemon(true);
        this.stateWatcher.start();
    }

    /**
     * Shutdown in-process resolver.
     *
     * @throws InterruptedException if stream can't be closed within deadline.
     */
    public void shutdown() throws InterruptedException {
        if (stateWatcher != null) {
            stateWatcher.interrupt();
        }
        connector.shutdown();
    }

    /**
     * Resolve a boolean flag.
     */
    public ProviderEvaluation<Boolean> booleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        ProviderEvaluation<Boolean> resolve = resolve(Boolean.class, key, ctx);
        return resolve;
    }

    /**
     * Resolve a string flag.
     */
    public ProviderEvaluation<String> stringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        ProviderEvaluation<String> resolve = resolve(String.class, key, ctx);
        return resolve;
    }

    /**
     * Resolve a double flag.
     */
    public ProviderEvaluation<Double> doubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return resolve(Double.class, key, ctx);
    }

    /**
     * Resolve an integer flag.
     */
    public ProviderEvaluation<Integer> integerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return resolve(Integer.class, key, ctx);
    }

    /**
     * Resolve an object flag.
     */
    public ProviderEvaluation<Value> objectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        final ProviderEvaluation<Object> evaluation = resolve(Object.class, key, ctx);

        return ProviderEvaluation.<Value>builder()
                .value(Value.objectToValue(evaluation.getValue()))
                .variant(evaluation.getVariant())
                .reason(evaluation.getReason())
                .errorCode(evaluation.getErrorCode())
                .errorMessage(evaluation.getErrorMessage())
                .flagMetadata(evaluation.getFlagMetadata())
                .build();
    }

    static QueueSource getConnector(final FlagdOptions options) {
        if (options.getCustomConnector() != null) {
            return options.getCustomConnector();
        }
        return options.getOfflineFlagSourcePath() != null
                        && !options.getOfflineFlagSourcePath().isEmpty()
                ? new FileQueueSource(options.getOfflineFlagSourcePath(), options.getOfflinePollIntervalMs())
                : new SyncStreamQueueSource(options);
    }

    private <T> ProviderEvaluation<T> resolve(Class<T> type, String key, EvaluationContext ctx) {
        try {
            EvaluationResult<T> evaluationResult = evaluator.evaluateFlag(type, key, ctx);
            return new ProviderEvaluation<>(
                    evaluationResult.getValue(),
                    evaluationResult.getVariant(),
                    evaluationResult.getReason(),
                    ErrorCode.valueOf(evaluationResult.getErrorCode()),
                    evaluationResult.getErrorMessage(),
                    evaluationResult.getFlagMetadata()
            );
        } catch (EvaluatorException e) {
            return ProviderEvaluation.<T>builder()
                    .reason(Reason.ERROR.toString())
                    .errorCode(ErrorCode.GENERAL)
                    .errorMessage("Error during wasm evaluation: " + e.getMessage())
                    .build();
        }
    }

    private Structure parseSyncContext(Struct syncContext) {
        if (syncContext != null) {
            try {
                return convertProtobufMapToStructure(syncContext.getFieldsMap());
            } catch (Exception exception) {
                log.error("Failed to parse metadataResponse, provider metadata may not be up-to-date");
            }
        }
        return new ImmutableStructure();
    }
}
