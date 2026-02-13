package dev.openfeature.contrib.providers.flagd.resolver.process;

import static dev.openfeature.contrib.tools.flagd.core.model.FeatureFlag.EMPTY_TARGETING_STRING;

import dev.openfeature.contrib.tools.flagd.api.Evaluator;
import dev.openfeature.contrib.tools.flagd.core.model.FeatureFlag;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Mock evaluator for testing that wraps a MockStorage.
 * Mimics the evaluation behavior of the old InProcessResolver.
 */
public class MockEvaluator implements Evaluator {

    private final MockStorage storage;
    private final Operator operator;

    public MockEvaluator(MockStorage storage) {
        this.storage = storage;
        this.operator = new Operator();
    }

    @Override
    public void setFlags(String flagConfigurationJson) {
        // No-op for mock - flags are set via constructor
    }

    @Override
    public List<String> setFlagsAndGetChangedKeys(String flagConfigurationJson) {
        // No-op for mock - flags are set via constructor
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> getFlagSetMetadata() {
        return storage.getFlagSetMetadata();
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
        final FeatureFlag flag = storage.getFlag(key);
        final Map<String, Object> flagSetMetadata = storage.getFlagSetMetadata();

        // missing flag
        if (flag == null) {
            return ProviderEvaluation.<T>builder()
                    .errorMessage("flag: " + key + " not found")
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .flagMetadata(getFlagMetadata(flagSetMetadata, null))
                    .build();
        }

        // state check
        if ("DISABLED".equals(flag.getState())) {
            return ProviderEvaluation.<T>builder()
                    .errorMessage("flag: " + key + " is disabled")
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .flagMetadata(getFlagMetadata(flagSetMetadata, flag))
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
                    resolvedVariant = jsonResolved.toString();
                    reason = Reason.TARGETING_MATCH.toString();
                }
            } catch (TargetingRuleException e) {
                String message = String.format("error evaluating targeting rule for flag %s", key);
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
                        .flagMetadata(getFlagMetadata(flagSetMetadata, flag))
                        .build();
            }
            String message = String.format("variant %s not found in flag with key %s", resolvedVariant, key);
            throw new GeneralError(message);
        }
        if (value instanceof Integer && type == Double.class) {
            value = ((Integer) value).doubleValue();
        } else if (value instanceof Double && type == Integer.class) {
            value = ((Double) value).intValue();
        }
        if (!type.isAssignableFrom(value.getClass())) {
            String message = "returning default variant for flagKey: %s, type not valid";
            throw new TypeMismatchError(message);
        }

        return ProviderEvaluation.<T>builder()
                .value((T) value)
                .variant(resolvedVariant)
                .reason(reason)
                .flagMetadata(getFlagMetadata(flagSetMetadata, flag))
                .build();
    }

    private ImmutableMetadata getFlagMetadata(Map<String, Object> flagSetMetadata, FeatureFlag flag) {
        ImmutableMetadata.ImmutableMetadataBuilder metadataBuilder = ImmutableMetadata.builder();
        for (Map.Entry<String, Object> entry : flagSetMetadata.entrySet()) {
            addEntryToMetadataBuilder(metadataBuilder, entry.getKey(), entry.getValue());
        }

        if (flag != null) {
            for (Map.Entry<String, Object> entry : flag.getMetadata().entrySet()) {
                addEntryToMetadataBuilder(metadataBuilder, entry.getKey(), entry.getValue());
            }
        }

        return metadataBuilder.build();
    }

    private void addEntryToMetadataBuilder(
            ImmutableMetadata.ImmutableMetadataBuilder metadataBuilder, String key, Object value) {
        if (value instanceof Number) {
            if (value instanceof Long) {
                metadataBuilder.addLong(key, (Long) value);
            } else if (value instanceof Double) {
                metadataBuilder.addDouble(key, (Double) value);
            } else if (value instanceof Integer) {
                metadataBuilder.addInteger(key, (Integer) value);
            } else if (value instanceof Float) {
                metadataBuilder.addFloat(key, (Float) value);
            }
        } else if (value instanceof Boolean) {
            metadataBuilder.addBoolean(key, (Boolean) value);
        } else if (value instanceof String) {
            metadataBuilder.addString(key, (String) value);
        }
    }
}
