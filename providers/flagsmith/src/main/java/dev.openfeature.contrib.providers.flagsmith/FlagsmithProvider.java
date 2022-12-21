package dev.openfeature.contrib.providers.flagsmith;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.flagsmith.FlagsmithClient;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.models.Flags;
import dev.openfeature.contrib.providers.flagsmith.exceptions.FlagsmithJsonException;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.OpenFeatureError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * FlagsmithProvider is the JAVA provider implementation for the feature flag solution Flagsmith.
 */
@Slf4j
class FlagsmithProvider implements FeatureProvider {

    private static final String NAME = "Flagsmith Provider";
    private static FlagsmithClient flagsmith;
    private FlagsmithProviderOptions options;

    public FlagsmithProvider(FlagsmithProviderOptions options) {
        this.options = options;
        FlagsmithClientConfigurer.validateOptions(options);
        flagsmith = FlagsmithClientConfigurer.initializeProvider(options);
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    @Override
    public List<Hook> getProviderHooks() {
        return FeatureProvider.super.getProviderHooks();
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(
        String key, Boolean defaultValue, EvaluationContext evaluationContext) {
        // When isUsingBooleanConfigValue the feature_state_value will be used as the flag value
        if (this.options.isUsingBooleanConfigValue()) {
            return resolveFlagsmithEvaluation(key, defaultValue, evaluationContext, Boolean.class);
        }
        // Otherwise the enabled field will be used as the boolean flag value
        try {
            Flags flags = getFlags(evaluationContext);
            //Todo once sdk has been updated to 5.1.2 these checks won't be relevant
            Boolean isFlagEnabled = flags.isFeatureEnabled(key);
            Boolean value = isFlagEnabled == null ? Boolean.FALSE : isFlagEnabled;
            Reason reason = isFlagEnabled != null && isFlagEnabled ? null : Reason.DISABLED;
            return buildEvaluation(value, null, reason, null);
        } catch (FlagsmithClientError flagsmithClientError) {
            return buildEvaluation(defaultValue, ErrorCode.GENERAL, Reason.ERROR, null);
        }
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(
        String key, String defaultValue, EvaluationContext evaluationContext) {
        return resolveFlagsmithEvaluation(key, defaultValue, evaluationContext, String.class);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(
        String key, Integer defaultValue, EvaluationContext evaluationContext) {
        return resolveFlagsmithEvaluation(key, defaultValue, evaluationContext, Integer.class);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(
        String key, Double defaultValue, EvaluationContext evaluationContext) {
        return resolveFlagsmithEvaluation(key, defaultValue, evaluationContext, Double.class);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(
        String key, Value defaultValue, EvaluationContext evaluationContext) {
        return resolveFlagsmithEvaluation(key, defaultValue, evaluationContext, Value.class);
    }

    /**
     * Get all the flags for a given environment or identity. The Method will use
     * getEnvironmentFlags from the Flagsmith sdk if no targeting key is provided
     * in the EvaluationContext. If a targeting key is provided then the
     * getIdentityFlags method will be used.
     *
     * @param ctx an EvaluationContext object with flag evaluation options
     * @return a Flagsmith Flags object with all the respective flags
     * @throws FlagsmithClientError Thrown when there are issue retrieving the flags
     *                              from Flagsmith
     */
    private Flags getFlags(EvaluationContext ctx) throws FlagsmithClientError {
        return Objects.isNull(ctx.getTargetingKey()) || ctx.getTargetingKey().isEmpty()
            ? flagsmith.getEnvironmentFlags()
            // Todo add traits when attributes are added to context
            : flagsmith.getIdentityFlags(ctx.getTargetingKey());
    }

    /**
     * Using the Flagsmith SDK this method resolves any type of flag into
     * a ProviderEvaluation. Since Flagsmith's sdk is agnostic of type
     * the flag needs to be cast to the correct type for OpenFeature's
     * ProviderEvaluation object.
     *
     * @param key          the string identifier for the flag being resolved
     * @param defaultValue the backup value if the flag can't be resolved
     * @param ctx          an EvaluationContext object with flag evaluation options
     * @param expectedType the expected data type of the flag as a class
     * @param <T>          the data type of the flag
     * @return a ProviderEvaluation object for the given flag type
     * @throws OpenFeatureError when flag evaluation fails
     */
    private <T> ProviderEvaluation<T> resolveFlagsmithEvaluation(
        String key, T defaultValue, EvaluationContext ctx, Class<?> expectedType
    ) throws OpenFeatureError {
        T flagValue = null;
        ErrorCode errorCode = null;
        Reason reason = null;
        String variationType = "";
        try {

            Flags flags = getFlags(ctx);
            // Check if the flag is enabled, return default value if not
            Boolean isFlagEnabled = flags.isFeatureEnabled(key);
            if (isFlagEnabled == null) {
                return buildEvaluation(defaultValue, ErrorCode.FLAG_NOT_FOUND, Reason.ERROR, null);
            }

            if (!isFlagEnabled) {
                return buildEvaluation(defaultValue, null, Reason.DISABLED, null);
            }

            Object value = flags.getFeatureValue(key);
            // Convert the value received from Flagsmith.
            flagValue = convertValue(value, expectedType);

        } catch (FlagsmithClientError flagsmithApiError) {
            return buildEvaluation(defaultValue, ErrorCode.GENERAL, Reason.ERROR, null);
        }

        return buildEvaluation(flagValue, errorCode, reason, variationType);
    }

    /**
     * Build a ProviderEvaluation object from the results provided by the
     * Flagsmith sdk.
     *
     * @param flagValue     the resolved flag either retrieved or set to the default
     * @param errorCode     error type for failed flag resolution, null if no issue
     * @param reason        description of issue resolving flag, null if no issue
     * @param variationType contains the name of the variation used for this flag
     * @param <T>           the data type of the flag
     * @return a ProviderEvaluation object for the given flag type
     */
    private <T> ProviderEvaluation<T> buildEvaluation(
        T flagValue, ErrorCode errorCode, Reason reason, String variationType) {
        ProviderEvaluation.ProviderEvaluationBuilder providerEvaluationBuilder =
            ProviderEvaluation.<T>builder()
                              .value(flagValue);

        if (errorCode != null) {
            providerEvaluationBuilder.errorCode(errorCode);
        }
        if (reason != null) {
            providerEvaluationBuilder.reason(reason.name());
        }
        if (variationType != null) {
            providerEvaluationBuilder.variant(variationType);
        }

        return providerEvaluationBuilder.build();
    }

    /**
     * The method convertValue is converting the object return by the Flagsmith client.
     *
     * @param value        the value we have received from Flagsmith
     * @param expectedType the type we expect for this value
     * @param <T>          the type we want to convert to
     * @return A converted object
     */
    private <T> T convertValue(Object value, Class<?> expectedType) {
        boolean isPrimitive = expectedType == Boolean.class
            || expectedType == String.class
            || expectedType == Integer.class
            || expectedType == Double.class;
        T flagValue;
        if (isPrimitive) {
            flagValue = (T) value;
        } else {
            flagValue = (T) objectToValue(value);
        }

        if (flagValue.getClass() != expectedType) {
            try {
                flagValue = mapJsonNodes(flagValue, expectedType);
            } catch (FlagsmithJsonException fje) {
                log.warn(fje.getMessage());
                throw new TypeMismatchError("Flag value had an unexpected type "
                    + flagValue.getClass() + ", expected " + expectedType + ".");
            }
        }
        return flagValue;
    }

    /**
     * The method objectToValue is wrapping an object into a Value.
     *
     * @param object the object you want to wrap
     * @return the wrapped object
     */
    @SneakyThrows
    private Value objectToValue(Object object) {
        if (object instanceof Value) {
            return (Value) object;
        } else if (object == null) {
            return null;
        } else if (object instanceof String) {
            return new Value((String) object);
        } else if (object instanceof Boolean) {
            return new Value((Boolean) object);
        } else if (object instanceof Integer) {
            return new Value((Integer) object);
        } else if (object instanceof Double) {
            return new Value((Double) object);
        } else if (object instanceof Structure) {
            return new Value((Structure) object);
        } else if (object instanceof List) {
            // need to translate each elem in list to a value
            return new Value(((List<Object>) object).stream()
                                                    .map(this::objectToValue)
                                                    .collect(Collectors.toList()));
        } else if (object instanceof Instant) {
            return new Value((Instant) object);
        } else if (object instanceof Map) {
            return new Value(mapToStructure((Map<String, Object>) object));
        } else if (object instanceof ObjectNode) {
            ObjectNode objectNode = (ObjectNode) object;
            return objectToValue(new ObjectMapper().convertValue(objectNode, Object.class));
        } else {
            throw new TypeMismatchError("Flag value " + object + " had unexpected type "
                + object.getClass() + ".");
        }
    }

    /**
     * When using identity flags the objects are returned as json nodes. This
     * method converts the nodes to primitive type objects.
     *
     * @param value        the value we have received from Flagsmith
     * @param expectedType the type we expect for this value
     * @param <T>          the type we want to convert to
     * @return A converted object
     */
    private <T> T mapJsonNodes(T value, Class<?> expectedType) {
        if (value.getClass() == BooleanNode.class && expectedType == Boolean.class) {
            return (T) Boolean.valueOf(((BooleanNode) value).asBoolean());
        }
        if (value.getClass() == TextNode.class && expectedType == String.class) {
            return (T) ((TextNode) value).asText();
        }
        if (value.getClass() == IntNode.class && expectedType == Integer.class) {
            return (T) Integer.valueOf(((IntNode) value).asInt());
        }
        if (value.getClass() == DoubleNode.class && expectedType == Double.class) {
            return (T) Double.valueOf(((DoubleNode) value).asDouble());
        }
        if (value.getClass() == ObjectNode.class && expectedType == Value.class) {
            return (T) objectToValue((Object) value);
        }
        throw new FlagsmithJsonException("Json object could not be cast to primitive type");
    }

    /**
     * The method mapToStructure transform a map coming from a JSON Object to a Structure type.
     *
     * @param map a JSON object return by the SDK
     * @return a Structure object in the SDK format
     */
    private Structure mapToStructure(Map<String, Object> map) {
        return new MutableStructure(
            map.entrySet().stream()
               .filter(e -> e.getValue() != null)
               .collect(Collectors.toMap(Map.Entry::getKey, e -> objectToValue(e.getValue()))));
    }
}