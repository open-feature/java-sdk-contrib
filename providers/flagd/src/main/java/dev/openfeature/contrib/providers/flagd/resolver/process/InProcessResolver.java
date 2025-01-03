package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.common.ConnectionEvent;
import dev.openfeature.contrib.providers.flagd.resolver.common.ConnectionState;
import dev.openfeature.contrib.providers.flagd.resolver.common.Util;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.FlagStore;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.Storage;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageStateChange;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.file.FileConnector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc.GrpcStreamConnector;
import dev.openfeature.contrib.providers.flagd.resolver.process.targeting.Operator;
import dev.openfeature.contrib.providers.flagd.resolver.process.targeting.TargetingRuleException;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.ParseError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag.EMPTY_TARGETING_STRING;

/**
 * Resolves flag values using
 * https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1.
 * Flags are evaluated locally.
 */
@Slf4j
public class InProcessResolver implements Resolver {
    private final Storage flagStore;
    private final Consumer<ConnectionEvent> onConnectionEvent;
    private final Operator operator;
    private final long deadline;
    private final ImmutableMetadata metadata;
    private final Supplier<Boolean> connectedSupplier;

    /**
     * Resolves flag values using
     * https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1.
     * Flags are evaluated locally.
     *
     * @param options           flagd options
     * @param connectedSupplier lambda providing current connection status from
     *                          caller
     * @param onConnectionEvent lambda which handles changes in the
     *                          connection/stream
     */
    public InProcessResolver(FlagdOptions options, final Supplier<Boolean> connectedSupplier,
                             Consumer<ConnectionEvent> onConnectionEvent) {
        this.flagStore = new FlagStore(getConnector(options));
        this.deadline = options.getDeadline();
        this.onConnectionEvent = onConnectionEvent;
        this.operator = new Operator();
        this.connectedSupplier = connectedSupplier;
        this.metadata = options.getSelector() == null ? null
                : ImmutableMetadata.builder()
                .addString("scope", options.getSelector())
                .build();
    }

    /**
     * Initialize in-process resolver.
     */
    public void init() throws Exception {
        flagStore.init();
        final Thread stateWatcher = new Thread(() -> {
            try {
                while (true) {
                    final StorageStateChange storageStateChange = flagStore.getStateQueue().take();
                    switch (storageStateChange.getStorageState()) {
                        case OK:
                            onConnectionEvent.accept(new ConnectionEvent(ConnectionState.CONNECTED,
                                    storageStateChange.getChangedFlagsKeys(),
                                    storageStateChange.getSyncMetadata()));
                            break;
                        case ERROR:
                            onConnectionEvent.accept(new ConnectionEvent(false));
                            break;
                        default:
                            log.info(String.format("Storage emitted unhandled status: %s",
                                    storageStateChange.getStorageState()));
                    }
                }
            } catch (InterruptedException e) {
                log.warn("Storage state watcher interrupted", e);
                Thread.currentThread().interrupt();
            }
        });
        stateWatcher.setDaemon(true);
        stateWatcher.start();

        // block till ready
        Util.busyWaitAndCheck(this.deadline, this.connectedSupplier);
    }

    /**
     * Shutdown in-process resolver.
     *
     * @throws InterruptedException if stream can't be closed within deadline.
     */
    public void shutdown() throws InterruptedException {
        flagStore.shutdown();
        onConnectionEvent.accept(new ConnectionEvent(false));
    }

    /**
     * Resolve a boolean flag.
     */
    public ProviderEvaluation<Boolean> booleanEvaluation(String key, Boolean defaultValue,
                                                         EvaluationContext ctx) {
        return resolve(Boolean.class, key, ctx);
    }

    /**
     * Resolve a string flag.
     */
    public ProviderEvaluation<String> stringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return resolve(String.class, key, ctx);
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

    static Connector getConnector(final FlagdOptions options) {
        if (options.getCustomConnector() != null) {
            return options.getCustomConnector();
        }
        return options.getOfflineFlagSourcePath() != null && !options.getOfflineFlagSourcePath().isEmpty()
                ? new FileConnector(options.getOfflineFlagSourcePath())
                : new GrpcStreamConnector(options);
    }

    private <T> ProviderEvaluation<T> resolve(Class<T> type, String key, EvaluationContext ctx) {
        final FeatureFlag flag = flagStore.getFlag(key);

        // missing flag
        if (flag == null) {
            return ProviderEvaluation.<T>builder()
                    .errorMessage("flag: " + key + " not found")
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .build();
        }

        // state check
        if ("DISABLED".equals(flag.getState())) {
            return ProviderEvaluation.<T>builder()
                    .errorMessage("flag: " + key + " is disabled")
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .build();
        }

        final String resolvedVariant;
        final String reason;

        if (EMPTY_TARGETING_STRING.equals(flag.getTargeting())) {
            resolvedVariant = flag.getDefaultVariant();
            reason = Reason.STATIC.toString();
        } else {
            try {
                final Object jsonResolved = operator.apply(key, flag.getTargeting(), ctx);
                if (jsonResolved == null) {
                    resolvedVariant = flag.getDefaultVariant();
                    reason = Reason.DEFAULT.toString();
                } else {
                    resolvedVariant = jsonResolved.toString(); // convert to string to support shorthand
                    reason = Reason.TARGETING_MATCH.toString();
                }
            } catch (TargetingRuleException e) {
                String message = String.format("error evaluating targeting rule for flag %s", key);
                log.debug(message, e);
                throw new ParseError(message);
            }
        }

        // check variant existence
        Object value = flag.getVariants().get(resolvedVariant);
        if (value == null) {
            String message = String.format("variant %s not found in flag with key %s", resolvedVariant, key);
            log.debug(message);
            throw new TypeMismatchError(message);
        }
        if (value instanceof Integer && type == Double.class) {
            // if this is an integer and we are trying to resolve a double, convert
            value = ((Integer) value).doubleValue();
        } else if (value instanceof Double && type == Integer.class) {
            // if this is a double and we are trying to resolve an integer, convert
            value = ((Double) value).intValue();
        }
        if (!type.isAssignableFrom(value.getClass())) {
            String message = "returning default variant for flagKey: %s, type not valid";
            log.debug(String.format(message, key));
            throw new TypeMismatchError(message);
        }

        final ProviderEvaluation.ProviderEvaluationBuilder<T> evaluationBuilder = ProviderEvaluation.<T>builder()
                .value((T) value)
                .variant(resolvedVariant)
                .reason(reason);

        return this.metadata == null ? evaluationBuilder.build()
                : evaluationBuilder.flagMetadata(this.metadata).build();
    }
}
