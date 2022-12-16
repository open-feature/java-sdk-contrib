package dev.openfeature.contrib.providers.flagsmith;

import com.flagsmith.FlagsmithClient;
import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.config.Retry;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.models.Flags;
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

class FlagsmithProvider implements FeatureProvider {

    private static final String NAME = "Flagsmith Provider";
    private static FlagsmithClient flagsmith;

    public FlagsmithProvider(FlagsmithProviderOptions options) {
        this.initializeProvider(options);
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
        return resolveFlagsmithEvaluation(key, defaultValue, evaluationContext, Boolean.class);
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
        return resolveFlagsmithEvaluation(key, defaultValue, evaluationContext, Object.class);
    }

    private <T> ProviderEvaluation<T> resolveFlagsmithEvaluation(
        String key, T defaultValue, EvaluationContext ctx, Class<?> expectedType
    ) throws OpenFeatureError {
        T flagValue = null;
        ErrorCode errorCode = null;
        Reason reason = null;
        String variationType = "";
        try {

            Flags flags = Objects.isNull(ctx.getTargetingKey()) || ctx.getTargetingKey().isEmpty()
                ? this.flagsmith.getEnvironmentFlags()
                : this.flagsmith.getIdentityFlags(ctx.getTargetingKey());
            // Check if the flag is enabled, return default value if not
            if (!flags.isFeatureEnabled(key)) {
                return ProviderEvaluation.<T>builder()
                                         .value(defaultValue)
                                         .reason(Reason.DISABLED.name())
                                         .build();
            }

            Object value = flags.getFeatureValue(key);
            // Convert the value received from Flagsmith.
            flagValue = convertValue(value, expectedType);

            if (flagValue.getClass() != expectedType) {
                throw new TypeMismatchError("Flag value " + key + " had unexpected type "
                    + flagValue.getClass() + ", expected " + expectedType + ".");
            }

        } catch (FlagsmithApiError flagsmithApiError) {
            flagValue = defaultValue;
            reason = Reason.ERROR;
            errorCode = ErrorCode.PARSE_ERROR;
        } catch (FlagsmithClientError flagsmithClientError) {
            flagValue = defaultValue;
            reason = Reason.ERROR;
            errorCode = ErrorCode.GENERAL;
        }

        return buildEvaluation(flagValue, errorCode, reason, variationType);
    }

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
     * initializeProvider is initializing the different class element used by the provider.
     *
     * @param options the options used to create the provider
     */
    private void initializeProvider(FlagsmithProviderOptions options) {
        FlagsmithClient.Builder flagsmithBuilder = FlagsmithClient
            .newBuilder();

        // Set main configuration settings
        if (options.getApiKey() != null) {
            flagsmithBuilder.setApiKey(options.getApiKey());
        }

        if (options.getHeaders() != null && !options.getHeaders().isEmpty()) {
            flagsmithBuilder.withCustomHttpHeaders(options.getHeaders());
        }

        if (options.getEnvFlagsCacheKey() != null) {
            FlagsmithCacheConfig flagsmithCacheConfig = initializeCacheConfig(options);
            flagsmithBuilder.withCache(flagsmithCacheConfig);
        }

        FlagsmithConfig flagsmithConfig = initializeConfig(options);
        flagsmithBuilder.withConfiguration(flagsmithConfig);

        this.flagsmith = flagsmithBuilder.build();
    }

    /**
     * Sets the cache related configuration for the provider using
     * the FlagsmithCacheConfig builder.
     *
     * @param options the options used to create the provider
     * @return a FlagsmithCacheConfig object containing the FlagsmithClient cache options
     */
    private FlagsmithCacheConfig initializeCacheConfig(FlagsmithProviderOptions options) {
        FlagsmithCacheConfig.Builder flagsmithCacheConfig = FlagsmithCacheConfig.newBuilder();

        // Set cache configuration settings
        if (options.getEnvFlagsCacheKey() != null) {
            flagsmithCacheConfig.enableEnvLevelCaching(options.getEnvFlagsCacheKey());
        }

        if (options.getExpireCacheAfterWrite() != 0
            && options.getExpireCacheAfterWriteTimeUnit() != null) {
            flagsmithCacheConfig.expireAfterAccess(
                options.getExpireCacheAfterWrite(),
                options.getExpireCacheAfterWriteTimeUnit());
        }

        if (options.getExpireCacheAfterAccess() != 0
            && options.getExpireCacheAfterAccessTimeUnit() != null) {
            flagsmithCacheConfig.expireAfterAccess(
                options.getExpireCacheAfterAccess(),
                options.getExpireCacheAfterAccessTimeUnit());
        }

        if (options.getMaxCacheSize() != 0) {
            flagsmithCacheConfig.maxSize(options.getMaxCacheSize());
        }

        return flagsmithCacheConfig.build();
    }

    /**
     * Set the configuration options for the FlagsmithClient using
     * the FlagsmithConfig builder.
     *
     * @param options The options used to create the provider
     * @return a FlagsmithConfig object with the FlagsmithClient settings
     */
    private FlagsmithConfig initializeConfig(FlagsmithProviderOptions options) {
        FlagsmithConfig.Builder flagsmithConfig = FlagsmithConfig.newBuilder();

        // Set client level configuration settings
        if (options.getBaseUri() != null) {
            flagsmithConfig.baseUri(options.getBaseUri());
        }

        if (options.getConnectTimeout() != 0) {
            flagsmithConfig.connectTimeout(options.getConnectTimeout());
        }

        if (options.getWriteTimeout() != 0) {
            flagsmithConfig.writeTimeout(options.getWriteTimeout());
        }

        if (options.getReadTimeout() != 0) {
            flagsmithConfig.readTimeout(options.getReadTimeout());
        }

        if (options.getSslSocketFactory() != null && options.getTrustManager() != null) {
            flagsmithConfig
                .sslSocketFactory(options.getSslSocketFactory(), options.getTrustManager());
        }

        if (options.getHttpInterceptor() != null) {
            flagsmithConfig.addHttpInterceptor(options.getHttpInterceptor());
        }

        if (options.getRetries() != 0) {
            flagsmithConfig.retries(new Retry(options.getRetries()));
        }

        if (options.isLocalEvaluation()) {
            flagsmithConfig.withLocalEvaluation(options.isLocalEvaluation());
        }

        if (options.getEnvironmentRefreshIntervalSeconds() != 0) {
            flagsmithConfig.withEnvironmentRefreshIntervalSeconds(options
                .getEnvironmentRefreshIntervalSeconds());
        }

        if (options.isEnableAnalytics()) {
            flagsmithConfig.withEnableAnalytics(options.isEnableAnalytics());
        }

        return flagsmithConfig.build();
    }

    /**
     * convertValue is converting the object return by the Flagsmith client.
     *
     * @param value        The value we have received
     * @param expectedType the type we expect for this value
     * @param <T>          the type we want to convert to.
     * @return A converted object
     */
    private <T> T convertValue(Object value, Class<?> expectedType) {
        boolean isPrimitive = expectedType == Boolean.class
            || expectedType == String.class
            || expectedType == Integer.class
            || expectedType == Double.class;

        if (isPrimitive) {
            return (T) value;
        }
        return (T) objectToValue(value);
    }

    /**
     * objectToValue is wrapping an object into a Value.
     *
     * @param object the object you want to wrap
     * @return the wrapped object
     */
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
        } else {
            throw new TypeMismatchError("Flag value " + object + " had unexpected type "
                + object.getClass() + ".");
        }
    }

    /**
     * mapToStructure transform a map coming from a JSON Object to a Structure type.
     *
     * @param map - JSON object return by the API
     * @return a Structure object in the SDK format
     */
    private Structure mapToStructure(Map<String, Object> map) {
        return new MutableStructure(
            map.entrySet().stream()
               .filter(e -> e.getValue() != null)
               .collect(Collectors.toMap(Map.Entry::getKey, e -> objectToValue(e.getValue()))));
    }
}