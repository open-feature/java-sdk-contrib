package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.FlagStore;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.Storage;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageState;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc.GrpcStreamConnector;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import io.github.jamsesso.jsonlogic.JsonLogic;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import lombok.extern.java.Log;

import java.util.function.Consumer;
import java.util.logging.Level;

import static dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag.EMPTY_TARGETING_STRING;

/**
 * flagd in-process resolver. Resolves feature flags in-process. Flags are retrieved from {@link Storage}, where the
 * {@link Storage} maintain flag configurations obtained from known source.
 */
@Log
public class InProcessResolver implements Resolver {
    private final Storage flagStore;
    private final Consumer<ProviderState> stateConsumer;
    private final JsonLogic jsonLogicHandler;

    /**
     * Initialize an in-process resolver.
     */
    public InProcessResolver(FlagdOptions options, Consumer<ProviderState> stateConsumer) {
        // currently we support gRPC connector
        this.flagStore = new FlagStore(new GrpcStreamConnector(options));
        this.stateConsumer = stateConsumer;
        jsonLogicHandler = new JsonLogic();
    }

    /**
     * Initialize in-process resolver.
     */
    public void init() throws Exception {
        flagStore.init();
        final Thread stateWatcher = new Thread(() -> {
            try {
                while (true) {
                    final StorageState storageState = flagStore.getStateQueue().take();
                    switch (storageState) {
                        case OK:
                            stateConsumer.accept(ProviderState.READY);
                            break;
                        case ERROR:
                            stateConsumer.accept(ProviderState.ERROR);
                            break;
                        case STALE:
                            // todo set stale state
                        default:
                            log.log(Level.INFO, String.format("Storage emitted unknown status: %s", storageState));
                    }
                }
            } catch (InterruptedException e) {
                log.log(Level.WARNING, "Storage state watcher interrupted", e);
            }
        });
        stateWatcher.setDaemon(true);
        stateWatcher.start();
    }

    /**
     * Shutdown in-process resolver.
     */
    public void shutdown() {
        flagStore.shutdown();
    }

    /**
     * Resolve a boolean flag.
     */
    public ProviderEvaluation<Boolean> booleanEvaluation(String key, Boolean defaultValue,
                                                         EvaluationContext ctx) {
        return resolve(Boolean.class, key, defaultValue, ctx);
    }

    /**
     * Resolve a string flag.
     */
    public ProviderEvaluation<String> stringEvaluation(String key, String defaultValue,
                                                       EvaluationContext ctx) {
        return resolve(String.class, key, defaultValue, ctx);
    }

    /**
     * Resolve a double flag.
     */
    public ProviderEvaluation<Double> doubleEvaluation(String key, Double defaultValue,
                                                       EvaluationContext ctx) {
        return resolve(Double.class, key, defaultValue, ctx);
    }

    /**
     * Resolve an integer flag.
     */
    public ProviderEvaluation<Integer> integerEvaluation(String key, Integer defaultValue,
                                                         EvaluationContext ctx) {
        return resolve(Integer.class, key, defaultValue, ctx);
    }

    /**
     * Resolve an object flag.
     */
    public ProviderEvaluation<Value> objectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        final ProviderEvaluation<Object> evaluation = resolve(Object.class, key, defaultValue, ctx);

        return ProviderEvaluation.<Value>builder()
                .value(Value.objectToValue(evaluation.getValue()))
                .variant(evaluation.getVariant())
                .reason(evaluation.getReason())
                .errorCode(evaluation.getErrorCode())
                .errorMessage(evaluation.getErrorMessage())
                .flagMetadata(evaluation.getFlagMetadata())
                .build();
    }

    private <T> ProviderEvaluation<T> resolve(Class<T> type, String key, T defaultValue,
                                              EvaluationContext ctx) {
        final FeatureFlag flag = flagStore.getFlag(key);

        // missing flag
        if (flag == null) {
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason(Reason.ERROR.toString())
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .errorMessage(String.format("requested flag could not be found: %s", key))
                    .build();
        }

        // state check
        if ("DISABLED".equals(flag.getState())) {
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason(Reason.DISABLED.toString())
                    .errorCode(ErrorCode.GENERAL)
                    .errorMessage(String.format("requested flag is disabled: %s", key))
                    .build();
        }


        final Object resolvedVariant;
        final String reason;

        if (EMPTY_TARGETING_STRING.equals(flag.getTargeting())) {
            resolvedVariant = flag.getDefaultVariant();
            reason = Reason.STATIC.toString();
        } else {
            try {
                final Object jsonResolved = jsonLogicHandler.apply(flag.getTargeting(), ctx.asObjectMap());
                if (jsonResolved == null) {
                    resolvedVariant = flag.getDefaultVariant();
                    reason = Reason.DEFAULT.toString();
                } else {
                    resolvedVariant = jsonResolved;
                    reason = Reason.TARGETING_MATCH.toString();
                }
            } catch (JsonLogicException e) {
                log.log(Level.FINE, "Error evaluating targeting rule", e);
                return ProviderEvaluation.<T>builder()
                        .value(defaultValue)
                        .reason(Reason.ERROR.toString())
                        .errorCode(ErrorCode.PARSE_ERROR)
                        .errorMessage(String.format("error parsing targeting rule for key: %s", key))
                        .build();
            }
        }

        // check variant existence
        Object value = flag.getVariants().get(resolvedVariant);
        if (value == null) {
            log.log(Level.FINE, String.format("variant %s not found in flag with key %s", resolvedVariant, key));
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason(Reason.ERROR.toString())
                    .errorCode(ErrorCode.TYPE_MISMATCH)
                    .errorMessage(String.format("requested flag is not of the evaluation type: %s", type.getName()))
                    .build();
        }

        if (!value.getClass().isAssignableFrom(type) || !(resolvedVariant instanceof String)) {
            log.log(Level.FINE, String.format("returning default variant for flagKey: %s, type not valid", key));
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason(Reason.ERROR.toString())
                    .errorCode(ErrorCode.TYPE_MISMATCH)
                    .errorMessage(String.format("flag %s is not of type %s", key, type.getName()))
                    .build();
        }

        return ProviderEvaluation.<T>builder()
                .value((T) value)
                .variant((String) resolvedVariant)
                .reason(reason)
                .build();
    }


}
