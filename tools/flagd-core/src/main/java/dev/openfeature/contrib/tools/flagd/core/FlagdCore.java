package dev.openfeature.contrib.tools.flagd.core;

import static dev.openfeature.contrib.tools.flagd.core.model.FeatureFlag.EMPTY_TARGETING_STRING;

import dev.openfeature.contrib.tools.flagd.api.Evaluator;
import dev.openfeature.contrib.tools.flagd.api.FlagStoreException;
import dev.openfeature.contrib.tools.flagd.core.model.FeatureFlag;
import dev.openfeature.contrib.tools.flagd.core.model.FlagParser;
import dev.openfeature.contrib.tools.flagd.core.model.FlagParsingResult;
import dev.openfeature.contrib.tools.flagd.core.targeting.Operator;
import dev.openfeature.contrib.tools.flagd.core.targeting.TargetingRuleException;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ParseError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Core flagd flag evaluation implementation.
 *
 * <p>This class provides the core logic for in-process flag evaluation,
 * allowing flags to be stored and evaluated locally. It implements the
 * {@link Evaluator} interface, which can be substituted with different
 * implementations if needed.
 *
 * <p>Usage:
 * <pre>{@code
 * FlagdCore core = new FlagdCore();
 * core.setFlags(flagConfigurationJson);
 * ProviderEvaluation<Boolean> result = core.resolveBooleanValue("myFlag", ctx);
 * }</pre>
 */
@Slf4j
public class FlagdCore implements Evaluator {

    private final ReentrantReadWriteLock sync = new ReentrantReadWriteLock();
    private final ReadLock readLock = sync.readLock();
    private final WriteLock writeLock = sync.writeLock();

    private final Map<String, FeatureFlag> flags = new HashMap<>();
    private final Map<String, Object> flagSetMetadata = new HashMap<>();

    private final Operator operator;
    private final boolean throwIfInvalid;

    /**
     * Construct a FlagdCore instance.
     */
    public FlagdCore() {
        this(false);
    }

    /**
     * Construct a FlagdCore instance.
     *
     * @param throwIfInvalid whether to throw an exception if flag configuration is invalid
     */
    public FlagdCore(boolean throwIfInvalid) {
        this.operator = new Operator();
        this.throwIfInvalid = throwIfInvalid;
    }

    /**
     * Set flag configurations from a JSON string.
     *
     * @param flagConfigurationJson the flag configuration JSON
     * @throws FlagStoreException if parsing fails
     */
    @Override
    public void setFlags(String flagConfigurationJson) throws FlagStoreException {
        try {
            FlagParsingResult parsingResult = FlagParser.parseString(flagConfigurationJson, throwIfInvalid);
            writeLock.lock();
            try {
                flags.clear();
                flags.putAll(parsingResult.getFlags());
                flagSetMetadata.clear();
                flagSetMetadata.putAll(parsingResult.getFlagSetMetadata());
            } finally {
                writeLock.unlock();
            }
        } catch (IOException e) {
            throw new FlagStoreException("Failed to parse flag configuration", e);
        }
    }

    /**
     * Set flag configurations and return the list of changed flag keys.
     *
     * @param flagConfigurationJson the flag configuration JSON
     * @return the list of changed flag keys
     * @throws FlagStoreException if parsing fails
     */
    @Override
    public List<String> setFlagsAndGetChangedKeys(String flagConfigurationJson) throws FlagStoreException {
        try {
            FlagParsingResult parsingResult = FlagParser.parseString(flagConfigurationJson, throwIfInvalid);
            List<String> changedKeys;
            writeLock.lock();
            try {
                changedKeys = getChangedFlagsKeys(parsingResult.getFlags());
                flags.clear();
                flags.putAll(parsingResult.getFlags());
                flagSetMetadata.clear();
                flagSetMetadata.putAll(parsingResult.getFlagSetMetadata());
            } finally {
                writeLock.unlock();
            }
            return changedKeys;
        } catch (IOException e) {
            throw new FlagStoreException("Failed to parse flag configuration", e);
        }
    }

    /**
     * Get the current flag set metadata.
     *
     * @return the flag set metadata (unmodifiable view)
     */
    @Override
    public Map<String, Object> getFlagSetMetadata() {
        readLock.lock();
        try {
            return Collections.unmodifiableMap(new HashMap<>(flagSetMetadata));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public ProviderEvaluation<Boolean> resolveBooleanValue(String flagKey, EvaluationContext ctx) {
        return resolve(Boolean.class, flagKey, ctx);
    }

    @Override
    public ProviderEvaluation<String> resolveStringValue(String flagKey, EvaluationContext ctx) {
        return resolve(String.class, flagKey, ctx);
    }

    @Override
    public ProviderEvaluation<Integer> resolveIntegerValue(String flagKey, EvaluationContext ctx) {
        return resolve(Integer.class, flagKey, ctx);
    }

    @Override
    public ProviderEvaluation<Double> resolveDoubleValue(String flagKey, EvaluationContext ctx) {
        return resolve(Double.class, flagKey, ctx);
    }

    @Override
    public ProviderEvaluation<Value> resolveObjectValue(String flagKey, EvaluationContext ctx) {
        final ProviderEvaluation<Object> evaluation = resolve(Object.class, flagKey, ctx);

        return ProviderEvaluation.<Value>builder()
                .value(Value.objectToValue(evaluation.getValue()))
                .variant(evaluation.getVariant())
                .reason(evaluation.getReason())
                .errorCode(evaluation.getErrorCode())
                .errorMessage(evaluation.getErrorMessage())
                .flagMetadata(evaluation.getFlagMetadata())
                .build();
    }

    private <T> ProviderEvaluation<T> resolve(Class<T> type, String key, EvaluationContext ctx) {
        final FeatureFlag flag;
        final Map<String, Object> currentFlagSetMetadata;

        readLock.lock();
        try {
            flag = flags.get(key);
            currentFlagSetMetadata = new HashMap<>(flagSetMetadata);
        } finally {
            readLock.unlock();
        }

        // missing flag
        if (flag == null) {
            return ProviderEvaluation.<T>builder()
                    .errorMessage("flag: " + key + " not found")
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .flagMetadata(getFlagMetadata(currentFlagSetMetadata, null))
                    .build();
        }

        // state check
        if ("DISABLED".equals(flag.getState())) {
            return ProviderEvaluation.<T>builder()
                    .errorMessage("flag: " + key + " is disabled")
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .flagMetadata(getFlagMetadata(currentFlagSetMetadata, null))
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
                        .flagMetadata(getFlagMetadata(currentFlagSetMetadata, flag))
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
                .flagMetadata(getFlagMetadata(currentFlagSetMetadata, flag))
                .build();
    }

    private static ImmutableMetadata getFlagMetadata(Map<String, Object> currentFlagSetMetadata, FeatureFlag flag) {
        ImmutableMetadata.ImmutableMetadataBuilder metadataBuilder = ImmutableMetadata.builder();
        for (Map.Entry<String, Object> entry : currentFlagSetMetadata.entrySet()) {
            addEntryToMetadataBuilder(metadataBuilder, entry.getKey(), entry.getValue());
        }

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

    private List<String> getChangedFlagsKeys(Map<String, FeatureFlag> newFlags) {
        // keys for flags that are new or have changed
        Stream<String> addedOrUpdated = newFlags.entrySet().stream()
                .filter(entry -> {
                    FeatureFlag oldFlag = flags.get(entry.getKey());
                    return oldFlag == null || !oldFlag.equals(entry.getValue());
                })
                .map(Map.Entry::getKey);

        // keys for flags that have been removed
        Stream<String> removed = flags.keySet().stream().filter(key -> !newFlags.containsKey(key));

        return Stream.concat(addedOrUpdated, removed).collect(Collectors.toList());
    }
}
