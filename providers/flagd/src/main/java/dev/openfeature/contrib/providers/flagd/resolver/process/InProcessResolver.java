package dev.openfeature.contrib.providers.flagd.resolver.process;

import static dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag.EMPTY_TARGETING_STRING;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.common.FlagdProviderEvent;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.FlagStore;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.Storage;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageQueryResult;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageStateChange;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.file.FileQueueSource;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.SyncStreamQueueSource;
import dev.openfeature.contrib.providers.flagd.resolver.process.targeting.Operator;
import dev.openfeature.contrib.providers.flagd.resolver.process.targeting.TargetingRuleException;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ParseError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves flag values using
 * https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1.
 * Flags are evaluated locally.
 */
@Slf4j
public class InProcessResolver implements Resolver {
    private final Storage flagStore;
    private final Consumer<FlagdProviderEvent> onConnectionEvent;
    private final Operator operator;
    private final String scope;

    /**
     * Resolves flag values using
     * https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1.
     * Flags are evaluated locally.
     *
     * @param options           flagd options
     * @param onConnectionEvent lambda which handles changes in the
     *                          connection/stream
     */
    public InProcessResolver(FlagdOptions options, Consumer<FlagdProviderEvent> onConnectionEvent) {
        this.flagStore = new FlagStore(getConnector(options, onConnectionEvent));
        this.onConnectionEvent = onConnectionEvent;
        this.operator = new Operator();
        this.scope = options.getSelector();
    }

    /**
     * Initialize in-process resolver.
     */
    public void init() throws Exception {
        flagStore.init();
        final Thread stateWatcher = new Thread(() -> {
            try {
                while (true) {
                    final StorageStateChange storageStateChange =
                            flagStore.getStateQueue().take();
                    switch (storageStateChange.getStorageState()) {
                        case OK:
                            log.debug("onConnectionEvent.accept ProviderEvent.PROVIDER_CONFIGURATION_CHANGED");
                            onConnectionEvent.accept(new FlagdProviderEvent(
                                    ProviderEvent.PROVIDER_CONFIGURATION_CHANGED,
                                    storageStateChange.getChangedFlagsKeys(),
                                    storageStateChange.getSyncMetadata()));
                            log.debug("post onConnectionEvent.accept ProviderEvent.PROVIDER_CONFIGURATION_CHANGED");
                            break;
                        case ERROR:
                            onConnectionEvent.accept(new FlagdProviderEvent(ProviderEvent.PROVIDER_ERROR));
                            break;
                        default:
                            log.warn(String.format(
                                    "Storage emitted unhandled status: %s", storageStateChange.getStorageState()));
                    }
                }
            } catch (InterruptedException e) {
                log.warn("Storage state watcher interrupted", e);
                Thread.currentThread().interrupt();
            }
        });
        stateWatcher.setDaemon(true);
        stateWatcher.start();
    }

    /**
     * Shutdown in-process resolver.
     *
     * @throws InterruptedException if stream can't be closed within deadline.
     */
    public void shutdown() throws InterruptedException {
        flagStore.shutdown();
    }

    /**
     * Resolve a boolean flag.
     */
    public ProviderEvaluation<Boolean> booleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
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

    static QueueSource getConnector(final FlagdOptions options, Consumer<FlagdProviderEvent> onConnectionEvent) {
        if (options.getCustomConnector() != null) {
            return options.getCustomConnector();
        }
        return options.getOfflineFlagSourcePath() != null
                        && !options.getOfflineFlagSourcePath().isEmpty()
                ? new FileQueueSource(options.getOfflineFlagSourcePath(), options.getOfflinePollIntervalMs())
                : new SyncStreamQueueSource(options, onConnectionEvent);
    }

    private <T> ProviderEvaluation<T> resolve(Class<T> type, String key, EvaluationContext ctx) {
        final StorageQueryResult storageQueryResult = flagStore.getFlag(key);
        final FeatureFlag flag = storageQueryResult.getFeatureFlag();

        // missing flag
        if (flag == null) {
            return ProviderEvaluation.<T>builder()
                    .errorMessage("flag: " + key + " not found")
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .flagMetadata(getFlagMetadata(storageQueryResult, scope))
                    .build();
        }

        // state check
        if ("DISABLED".equals(flag.getState())) {
            return ProviderEvaluation.<T>builder()
                    .errorMessage("flag: " + key + " is disabled")
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .flagMetadata(getFlagMetadata(storageQueryResult, scope))
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
            if (StringUtils.isEmpty(resolvedVariant) && StringUtils.isEmpty(flag.getDefaultVariant())) {
                return ProviderEvaluation.<T>builder()
                        .reason(Reason.ERROR.toString())
                        .errorCode(ErrorCode.FLAG_NOT_FOUND)
                        .errorMessage("Flag '" + key + "' has no default variant defined, will use code default")
                        .flagMetadata(getFlagMetadata(storageQueryResult, scope))
                        .build();
            }

            String message = String.format("variant %s not found in flag with key %s", resolvedVariant, key);
            log.debug(message);
            throw new GeneralError(message);
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

        return ProviderEvaluation.<T>builder()
                .value((T) value)
                .variant(resolvedVariant)
                .reason(reason)
                .flagMetadata(getFlagMetadata(storageQueryResult, scope))
                .build();
    }

    private static ImmutableMetadata getFlagMetadata(StorageQueryResult storageQueryResult, String scope) {
        ImmutableMetadata.ImmutableMetadataBuilder metadataBuilder = ImmutableMetadata.builder();
        for (Map.Entry<String, Object> entry :
                storageQueryResult.getFlagSetMetadata().entrySet()) {
            addEntryToMetadataBuilder(metadataBuilder, entry.getKey(), entry.getValue());
        }

        if (scope != null) {
            metadataBuilder.addString("scope", scope);
        }

        FeatureFlag flag = storageQueryResult.getFeatureFlag();
        if (flag != null) {
            for (Map.Entry<String, Object> entry : flag.getMetadata().entrySet()) {
                addEntryToMetadataBuilder(metadataBuilder, entry.getKey(), entry.getValue());
            }
        }

        return metadataBuilder.build();
    }

    private static void addEntryToMetadataBuilder(
            ImmutableMetadata.ImmutableMetadataBuilder metadataBuilder, String key, Object value) {
        if (value instanceof Number) {
            if (value instanceof Long) {
                metadataBuilder.addLong(key, (Long) value);
                return;
            } else if (value instanceof Double) {
                metadataBuilder.addDouble(key, (Double) value);
                return;
            } else if (value instanceof Integer) {
                metadataBuilder.addInteger(key, (Integer) value);
                return;
            } else if (value instanceof Float) {
                metadataBuilder.addFloat(key, (Float) value);
                return;
            }
        } else if (value instanceof Boolean) {
            metadataBuilder.addBoolean(key, (Boolean) value);
            return;
        } else if (value instanceof String) {
            metadataBuilder.addString(key, (String) value);
            return;
        }
        throw new IllegalArgumentException(
                "The type of the Metadata entry with key " + key + " and value " + value + " is not supported");
    }
}
