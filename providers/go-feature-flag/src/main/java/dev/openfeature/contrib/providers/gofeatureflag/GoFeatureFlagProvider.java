package dev.openfeature.contrib.providers.gofeatureflag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.openfeature.contrib.providers.gofeatureflag.bean.BeanUtils;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagRequest;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagUser;
import dev.openfeature.contrib.providers.gofeatureflag.events.Event;
import dev.openfeature.contrib.providers.gofeatureflag.events.Events;
import dev.openfeature.contrib.providers.gofeatureflag.events.EventsPublisher;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.exception.NotPresentInCache;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.OpenFeatureError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.net.HttpURLConnection.*;

/**
 * GoFeatureFlagProvider is the JAVA provider implementation for the feature flag solution GO Feature Flag.
 */
@Slf4j
public class GoFeatureFlagProvider implements FeatureProvider {
    public static final long DEFAULT_FLUSH_INTERVAL_MS = Duration.ofMinutes(1).toMillis();
    public static final int DEFAULT_MAX_PENDING_EVENTS = 10000;
    public static final long DEFAULT_CACHE_TTL_MS = 1000;
    public static final int DEFAULT_CACHE_CONCURRENCY_LEVEL = 1;
    public static final int DEFAULT_CACHE_INITIAL_CAPACITY = 100;
    public static final int DEFAULT_CACHE_MAXIMUM_SIZE = 10000;
    protected static final String CACHED_REASON = Reason.CACHED.name();
    private static final String NAME = "GO Feature Flag Provider";
    private static final ObjectMapper requestMapper = new ObjectMapper();
    private static final ObjectMapper responseMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final GoFeatureFlagProviderOptions options;
    private HttpUrl parsedEndpoint;
    // httpClient is the instance of the OkHttpClient used by the provider
    private OkHttpClient httpClient;
    // apiKey contains the token to use while calling GO Feature Flag relay proxy
    private String apiKey;
    @Getter(AccessLevel.PROTECTED)
    private Cache<String, ProviderEvaluation<?>> cache;
    @Getter(AccessLevel.PROTECTED)
    private EventsPublisher<Event> eventsPublisher;
    private ProviderState state = ProviderState.NOT_READY;

    /**
     * Constructor of the provider.
     *
     * @param options - options to configure the provider
     * @throws InvalidOptions - if options are invalid
     */
    public GoFeatureFlagProvider(GoFeatureFlagProviderOptions options) throws InvalidOptions {
        this.validateInputOptions(options);
        this.options = options;
    }

    /**
     * validateInputOptions is validating the different options provided when creating the provider.
     *
     * @param options - Options used while creating the provider
     * @throws InvalidOptions  - if no options are provided
     * @throws InvalidEndpoint - if the endpoint provided is not valid
     */
    private void validateInputOptions(GoFeatureFlagProviderOptions options) throws InvalidEndpoint, InvalidOptions {
        if (options == null) {
            throw new InvalidOptions("No options provided");
        }

        if (options.getEndpoint() == null || "".equals(options.getEndpoint())) {
            throw new InvalidEndpoint("endpoint is a mandatory field when initializing the provider");
        }

        if (options.getFlushIntervalMs() != null && options.getFlushIntervalMs() <= 0) {
            throw new InvalidOptions("flushIntervalMs must be larger than 0");
        }

        if (Boolean.FALSE.equals(options.getEnableCache()) && options.getFlushIntervalMs() != null) {
            throw new InvalidOptions("flushIntervalMs not used when cache is disabled");
        }

        if (options.getMaxPendingEvents() != null && options.getMaxPendingEvents() <= 0) {
            throw new InvalidOptions("maxPendingEvents must be larger than 0");
        }
    }

    /**
     * buildDefaultCache is building a default cache configuration
     * @return the default cache configuration
     */
    private Cache<String, ProviderEvaluation<?>> buildDefaultCache() {
        return CacheBuilder.newBuilder()
                .concurrencyLevel(DEFAULT_CACHE_CONCURRENCY_LEVEL)
                .initialCapacity(DEFAULT_CACHE_INITIAL_CAPACITY).maximumSize(DEFAULT_CACHE_MAXIMUM_SIZE)
                .expireAfterWrite(Duration.ofMillis(DEFAULT_CACHE_TTL_MS))
                .build();
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
            String key, Boolean defaultValue, EvaluationContext evaluationContext
    ) {
        return getEvaluation(key, defaultValue, evaluationContext, Boolean.class);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(
            String key, String defaultValue, EvaluationContext evaluationContext
    ) {
        return getEvaluation(key, defaultValue, evaluationContext, String.class);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(
            String key, Integer defaultValue, EvaluationContext evaluationContext
    ) {
        return getEvaluation(key, defaultValue, evaluationContext, Integer.class);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(
            String key, Double defaultValue, EvaluationContext evaluationContext
    ) {
        return getEvaluation(key, defaultValue, evaluationContext, Double.class);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(
            String key, Value defaultValue, EvaluationContext evaluationContext
    ) {
        return getEvaluation(key, defaultValue, evaluationContext, Value.class);
    }

    /**
     * buildCacheKey is creating the entry key of the cache
     * @param key - the name of your feature flag
     * @param userKey - a representation of your user
     * @return the cache key
     */
    private String buildCacheKey(String key, String userKey) {
        return key + "," + userKey;
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        FeatureProvider.super.initialize(evaluationContext);

        // Register JavaTimeModule to be able to deserialized java.time.Instant Object
        requestMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        requestMapper.enable(SerializationFeature.INDENT_OUTPUT);
        requestMapper.registerModule(new JavaTimeModule());

        // init httpClient to call the GO Feature Flag API
        int timeout = options.getTimeout() == 0 ? 10000 : options.getTimeout();
        long keepAliveDuration = options.getKeepAliveDuration() == null ? 7200000 : options.getKeepAliveDuration();
        int maxIdleConnections = options.getMaxIdleConnections() == 0 ? 1000 : options.getMaxIdleConnections();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .callTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .connectionPool(new ConnectionPool(maxIdleConnections, keepAliveDuration, TimeUnit.MILLISECONDS))
                .build();

        this.parsedEndpoint = HttpUrl.parse(options.getEndpoint());
        if (this.parsedEndpoint == null) {
            throw new InvalidEndpoint();
        }
        this.apiKey = options.getApiKey();
        boolean enableCache = options.getEnableCache() == null || options.getEnableCache();
        if (enableCache) {
            if (options.getCacheBuilder() != null) {
                this.cache = options.getCacheBuilder().build();
            } else {
                this.cache = buildDefaultCache();
            }
            long flushIntervalMs = options.getFlushIntervalMs() == null
                    ? DEFAULT_FLUSH_INTERVAL_MS : options.getFlushIntervalMs();
            int maxPendingEvents = options.getMaxPendingEvents() == null
                    ? DEFAULT_MAX_PENDING_EVENTS : options.getMaxPendingEvents();
            Consumer<List<Event>> publisher = this::publishEvents;
            eventsPublisher = new EventsPublisher<>(publisher, flushIntervalMs, maxPendingEvents);
        }
        state = ProviderState.READY;
        log.info("finishing initializing provider, state: {}", state);
    }

    @Override
    public ProviderState getState() {
        return state;
    }

    /**
     * getEvaluation is the function resolving the flag, it will 1st check in the cache and if it is not available
     * will call the evaluation endpoint to get the value of the flag
     *
     * @param key               - name of the feature flag
     * @param defaultValue      - value used if something is not working as expected
     * @param evaluationContext - EvaluationContext used for the request
     * @param expectedType      - type expected for the value
     * @param <T>               the type of your evaluation
     * @return a ProviderEvaluation that contains the open-feature response
     */
    private <T> ProviderEvaluation<T> getEvaluation(
            String key, T defaultValue, EvaluationContext evaluationContext, Class<?> expectedType) {
        if (!ProviderState.READY.equals(state)) {
            ErrorCode errorCode = ErrorCode.PROVIDER_NOT_READY;
            if (ProviderState.ERROR.equals(state)) {
                errorCode = ErrorCode.GENERAL;
            }
            return ProviderEvaluation.<T>builder()
                    .errorCode(errorCode)
                    .reason(errorCode.name())
                    .value(defaultValue)
                    .build();
        }

        GoFeatureFlagUser user = GoFeatureFlagUser.fromEvaluationContext(evaluationContext);
        if (cache == null) {
            // Cache is disabled we return directly the remote evaluation
            EvaluationResponse<T> proxyRes = resolveEvaluationGoFeatureFlagProxy(key, defaultValue, user, expectedType);
            return proxyRes.getProviderEvaluation();
        }

        String cacheKey = null;
        try {
            cacheKey = buildCacheKey(key, BeanUtils.buildKey(user));
            ProviderEvaluation<?> cacheResponse = cache.getIfPresent(cacheKey);
            if (cacheResponse == null) {
                throw new NotPresentInCache(cacheKey);
            }
            cacheResponse.setReason(CACHED_REASON);
            eventsPublisher.add(Event.builder()
                    .key(key)
                    .kind("feature")
                    .contextKind(user.isAnonymous() ? "anonymousUser" : "user")
                    .defaultValue(defaultValue)
                    .variation(cacheResponse.getVariant())
                    .value(cacheResponse.getValue())
                    .userKey(user.getKey())
                    .creationDate(System.currentTimeMillis())
                    .build()
            );
            return (ProviderEvaluation<T>) cacheResponse;
        } catch (Exception e) {
            if (!(e instanceof NotPresentInCache)) {
                log.error("Error while trying to retrieve from the cache, trying to do a remote evaluation", e);
            }
            EvaluationResponse<T> proxyRes = resolveEvaluationGoFeatureFlagProxy(key, defaultValue, user, expectedType);
            if (Boolean.TRUE.equals(proxyRes.getCachable()) && cacheKey != null) {
                cache.put(cacheKey, proxyRes.getProviderEvaluation());
            }
            return proxyRes.getProviderEvaluation();
        }
    }

    /**
     * resolveEvaluationGoFeatureFlagProxy is calling the GO Feature Flag API to retrieve the flag value.
     *
     * @param key          - name of the feature flag
     * @param defaultValue - value used if something is not working as expected
     * @param user         - user (containing EvaluationContext) used for the request
     * @param expectedType - type expected for the value
     * @return a ProviderEvaluation that contains the open-feature response
     * @throws OpenFeatureError - if an error happen
     */
    private <T> EvaluationResponse<T> resolveEvaluationGoFeatureFlagProxy(
            String key, T defaultValue, GoFeatureFlagUser user, Class<?> expectedType
    ) throws OpenFeatureError {
        try {
            GoFeatureFlagRequest<T> goffRequest = new GoFeatureFlagRequest<>(user, defaultValue);

            HttpUrl url = this.parsedEndpoint.newBuilder()
                    .addEncodedPathSegment("v1")
                    .addEncodedPathSegment("feature")
                    .addEncodedPathSegment(key)
                    .addEncodedPathSegment("eval")
                    .build();

            Request.Builder reqBuilder = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(
                            requestMapper.writeValueAsBytes(goffRequest),
                            MediaType.get("application/json; charset=utf-8")));

            if (this.apiKey != null && !"".equals(this.apiKey)) {
                reqBuilder.addHeader("Authorization", "Bearer " + this.apiKey);
            }

            try (Response response = this.httpClient.newCall(reqBuilder.build()).execute()) {
                if (response.code() == HTTP_UNAUTHORIZED) {
                    throw new GeneralError("invalid token used to contact GO Feature Flag relay proxy instance");
                }
                if (response.code() >= HTTP_BAD_REQUEST) {
                    throw new GeneralError("impossible to contact GO Feature Flag relay proxy instance");
                }

                ResponseBody responseBody = response.body();
                String body = responseBody != null ? responseBody.string() : "";
                GoFeatureFlagResponse goffResp =
                        responseMapper.readValue(body, GoFeatureFlagResponse.class);

                if (Reason.DISABLED.name().equalsIgnoreCase(goffResp.getReason())) {
                    // we don't set a variant since we are using the default value, and we are not able to know
                    // which variant it is.
                    ProviderEvaluation<T> providerEvaluation = ProviderEvaluation.<T>builder()
                            .value(defaultValue)
                            .variant(goffResp.getVariationType())
                            .reason(Reason.DISABLED.name()).build();

                    return EvaluationResponse.<T>builder()
                            .providerEvaluation(providerEvaluation).cachable(goffResp.getCacheable()).build();
                }

                if (ErrorCode.FLAG_NOT_FOUND.name().equalsIgnoreCase(goffResp.getErrorCode())) {
                    throw new FlagNotFoundError("Flag " + key + " was not found in your configuration");
                }

                // Convert the value received from the API.
                T flagValue = convertValue(goffResp.getValue(), expectedType);

                if (flagValue.getClass() != expectedType) {
                    throw new TypeMismatchError("Flag value " + key + " had unexpected type "
                            + flagValue.getClass() + ", expected " + expectedType + ".");
                }

                ProviderEvaluation<T> providerEvaluation = ProviderEvaluation.<T>builder()
                        .errorCode(mapErrorCode(goffResp.getErrorCode()))
                        .reason(goffResp.getReason())
                        .value(flagValue)
                        .variant(goffResp.getVariationType())
                        .flagMetadata(this.convertFlagMetadata(goffResp.getMetadata()))
                        .build();

                return EvaluationResponse.<T>builder()
                        .providerEvaluation(providerEvaluation).cachable(goffResp.getCacheable()).build();
            }
        } catch (IOException e) {
            throw new GeneralError("unknown error while retrieving flag " + key, e);
        }
    }

    /**
     * mapErrorCode is mapping the errorCode in string received by the API to our internal SDK ErrorCode enum.
     *
     * @param errorCode - string of the errorCode received from the API
     * @return an item from the enum
     */
    private ErrorCode mapErrorCode(String errorCode) {
        try {
            return ErrorCode.valueOf(errorCode);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * convertFlagMetadata is converting the flagMetadata object received from the server
     * to an ImmutableMetadata format known by Open Feature.
     *
     * @param flagMetadata - metadata received from the server
     * @return a converted metadata object.
     */
    private ImmutableMetadata convertFlagMetadata(Map<String, Object> flagMetadata) {
        ImmutableMetadata.ImmutableMetadataBuilder builder = ImmutableMetadata.builder();
        flagMetadata.forEach((k, v) -> {
            if (v instanceof Long) {
                builder.addLong(k, (Long) v);
            } else if (v instanceof Integer) {
                builder.addInteger(k, (Integer) v);
            } else if (v instanceof Float) {
                builder.addFloat(k, (Float) v);
            } else if (v instanceof Double) {
                builder.addDouble(k, (Double) v);
            } else if (v instanceof Boolean) {
                builder.addBoolean(k, (Boolean) v);
            } else {
                builder.addString(k, v.toString());
            }
        });
        return builder.build();
    }

    /**
     * convertValue is converting the object return by the proxy response in the right type.
     *
     * @param value        - The value we have received
     * @param expectedType - the type we expect for this value
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
            return new Value(((List<Object>) object).stream().map(this::objectToValue).collect(Collectors.toList()));
        } else if (object instanceof Instant) {
            return new Value((Instant) object);
        } else if (object instanceof Map) {
            return new Value(mapToStructure((Map<String, Object>) object));
        } else {
            throw new ClassCastException("Could not cast Object to Value");
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

    /**
     * publishEvents is calling the GO Feature Flag data/collector api to store the flag usage for analytics.
     *
     * @param eventsList - list of the event to send to GO Feature Flag
     */
    private void publishEvents(List<Event> eventsList) {
        try {
            Events events = new Events(eventsList);
            HttpUrl url = this.parsedEndpoint.newBuilder()
                    .addEncodedPathSegment("v1")
                    .addEncodedPathSegment("data")
                    .addEncodedPathSegment("collector")
                    .build();

            Request.Builder reqBuilder = null;

            reqBuilder = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(
                            requestMapper.writeValueAsBytes(events),
                            MediaType.get("application/json; charset=utf-8")));

            if (this.apiKey != null && !"".equals(this.apiKey)) {
                reqBuilder.addHeader("Authorization", "Bearer " + this.apiKey);
            }

            try (Response response = this.httpClient.newCall(reqBuilder.build()).execute()) {
                if (response.code() == HTTP_UNAUTHORIZED) {
                    throw new GeneralError("Unauthorized");
                }
                if (response.code() >= HTTP_BAD_REQUEST) {
                    throw new GeneralError("Bad request: " + response.body());
                }

                if (response.code() == HTTP_OK) {
                    log.info("Published {} events successfully: {}", eventsList.size(), response.body());
                }
            } catch (IOException e) {
                throw new GeneralError("Impossible to send the usage data to GO Feature Flag", e);
            }
        } catch (JsonProcessingException e) {
            throw new GeneralError("Impossible to convert data collector events", e);
        }
    }

    @Override
    public void shutdown() {
        log.info("shutdown");
        try {
            if (eventsPublisher != null) {
                eventsPublisher.shutdown();
            }
        } catch (Exception e) {
            log.error("error publishing events on shutdown", e);
        }
    }
}
