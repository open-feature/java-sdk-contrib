package org.gofeatureflag.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.openfeature.javasdk.*;
import dev.openfeature.javasdk.exceptions.FlagNotFoundError;
import dev.openfeature.javasdk.exceptions.GeneralError;
import dev.openfeature.javasdk.exceptions.OpenFeatureError;
import dev.openfeature.javasdk.exceptions.TypeMismatchError;
import okhttp3.*;
import org.gofeatureflag.provider.bean.GoFeatureFlagRequest;
import org.gofeatureflag.provider.bean.GoFeatureFlagResponse;
import org.gofeatureflag.provider.bean.GoFeatureFlagUser;
import org.gofeatureflag.provider.exception.InvalidEndpoint;
import org.gofeatureflag.provider.exception.InvalidOptions;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;

public class GoFeatureFlagProvider implements FeatureProvider {
    private static final String NAME = "GO Feature Flag Provider";
    private static final ObjectMapper requestMapper = new ObjectMapper();
    private static final ObjectMapper responseMapper = new ObjectMapper();
    private final String endpoint;
    // httpClient is the instance of the OkHttpClient used by the provider
    private OkHttpClient httpClient;

    public GoFeatureFlagProvider(GoFeatureFlagProviderOptions options) throws InvalidOptions {
        this.validateInputOptions(options);
        this.endpoint = options.getEndpoint();
        this.initializeProvider(options);
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
    }

    /**
     * initializeProvider is initializing the different class element used by the provider
     *
     * @param options - Options used while creating the provider
     */
    private void initializeProvider(GoFeatureFlagProviderOptions options) {
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
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext evaluationContext) {
        return resolveEvaluationGoFeatureFlagProxy(key, defaultValue, evaluationContext, Boolean.class);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext evaluationContext) {
        return resolveEvaluationGoFeatureFlagProxy(key, defaultValue, evaluationContext, String.class);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext evaluationContext) {
        return resolveEvaluationGoFeatureFlagProxy(key, defaultValue, evaluationContext, Integer.class);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext evaluationContext) {
        return resolveEvaluationGoFeatureFlagProxy(key, defaultValue, evaluationContext, Double.class);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext evaluationContext) {
        return resolveEvaluationGoFeatureFlagProxy(key, defaultValue, evaluationContext, Value.class);
    }

    /**
     * resolveEvaluationGoFeatureFlagProxy is calling the GO Feature Flag API to retrieve the flag value
     *
     * @param key          - name of the feature flag
     * @param defaultValue - value used if something is not working as expected
     * @param ctx          - EvaluationContext used for the request
     * @param expectedType - type expected for the value
     * @return a ProviderEvaluation that contains the open-feature response
     * @throws OpenFeatureError - if an error happen
     */
    private <T> ProviderEvaluation<T> resolveEvaluationGoFeatureFlagProxy(String key, T defaultValue, EvaluationContext ctx, Class<? extends Object> expectedType) throws OpenFeatureError {
        try {
            GoFeatureFlagUser user = GoFeatureFlagUser.fromEvaluationContext(ctx);
            GoFeatureFlagRequest<T> goffRequest = new GoFeatureFlagRequest<T>(user, defaultValue);
            HttpUrl url = Objects.requireNonNull(HttpUrl.parse(this.endpoint))
                    .newBuilder()
                    .addEncodedPathSegment("v1")
                    .addEncodedPathSegment("feature")
                    .addEncodedPathSegment(key)
                    .addEncodedPathSegment("eval")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestMapper.writeValueAsBytes(goffRequest), MediaType.get("application/json; charset=utf-8")))
                    .build();

            try (Response response = this.httpClient.newCall(request).execute()) {
                if (response.code() >= HTTP_BAD_REQUEST) {
                    throw new GeneralError("impossible to contact GO Feature Flag relay proxy instance");
                }
                GoFeatureFlagResponse<T> goffResp = responseMapper.readValue(response.body().string(), GoFeatureFlagResponse.class);

                if (Reason.DISABLED.name().equalsIgnoreCase(goffResp.getReason())) {
                    // we don't set a variant since we are using the default value, and we are not able to know
                    // which variant it is.
                    return ProviderEvaluation.<T>builder().value(defaultValue).reason(Reason.DISABLED).build();
                }

                if (ErrorCode.FLAG_NOT_FOUND.name().equalsIgnoreCase(goffResp.getErrorCode())) {
                    throw new FlagNotFoundError("Flag " + key + " was not found in your configuration");
                }

                boolean isPrimitive = expectedType == Boolean.class ||
                        expectedType == String.class ||
                        expectedType == Integer.class ||
                        expectedType == Double.class;

                // Convert the value received from the API.
                T flagValue = isPrimitive
                        ? goffResp.getValue() : (T) objectToValue(goffResp.getValue());

                if (flagValue.getClass() != expectedType) {
                    throw new TypeMismatchError("Flag value " + key + " had unexpected type " + flagValue.getClass() + ", expected " + expectedType + ".");
                }

                return ProviderEvaluation.<T>builder()
                        .errorCode(goffResp.getErrorCode())
                        .reason(mapReason(goffResp.getReason()))
                        .value(flagValue)
                        .variant(goffResp.getVariationType())
                        .build();

            }
        } catch (IOException e) {
            throw new GeneralError("unknown error while retrieving flag " + key);
        }
    }


    /**
     * mapReason is mapping the reason in string received by the API
     * to our internal SDK reason enum.
     *
     * @param reason - string of the reason received from the API
     * @return an item from the enum
     */
    private Reason mapReason(String reason) {
        try {
            return Reason.valueOf(reason);
        } catch (IllegalArgumentException e) {
            return Reason.UNKNOWN;
        }
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
     * mapToStructure transform a map coming from a JSON Object to a Structure type
     *
     * @param map - JSON object return by the API
     * @return a Structure object in the SDK format
     */
    private Structure mapToStructure(Map<String, Object> map) {
        return new Structure(
                map.entrySet().stream().filter(e -> e.getValue() != null).collect(Collectors.toMap(Map.Entry::getKey, e -> objectToValue(e.getValue()))));
    }
}
