package dev.openfeature.contrib.providers.gofeatureflag.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.net.HttpHeaders;
import dev.openfeature.contrib.providers.gofeatureflag.EvaluationResponse;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.bean.ConfigurationChange;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagRequest;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagUser;
import dev.openfeature.contrib.providers.gofeatureflag.exception.ConfigurationChangeEndpointNotFound;
import dev.openfeature.contrib.providers.gofeatureflag.exception.ConfigurationChangeEndpointUnknownErr;
import dev.openfeature.contrib.providers.gofeatureflag.exception.GoFeatureFlagException;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.hook.events.Event;
import dev.openfeature.contrib.providers.gofeatureflag.hook.events.Events;
import dev.openfeature.contrib.providers.gofeatureflag.util.MetadataUtil;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.OpenFeatureError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import lombok.Builder;
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
import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static dev.openfeature.sdk.Value.objectToValue;


/**
 * GoFeatureFlagController is the layer to contact the APIs and get the data
 * from the GoFeatureFlagProvider.
 */
@Slf4j
@SuppressWarnings({"checkstyle:NoFinalizer"})
public class GoFeatureFlagController {
    public static final String APPLICATION_JSON = "application/json";
    public static final ObjectMapper requestMapper = new ObjectMapper();
    private static final ObjectMapper responseMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String BEARER_TOKEN = "Bearer ";

    /**
     * apiKey contains the token to use while calling GO Feature Flag relay proxy.
     */
    private final String apiKey;
    /**
     * httpClient is the instance of the OkHttpClient used by the provider.
     */
    private final OkHttpClient httpClient;
    private final HttpUrl parsedEndpoint;

    /**
     * etag contains the etag of the configuration, if null, it means that the configuration has never been retrieved.
     */
    private String etag;


    /**
     * GoFeatureFlagController is the constructor of the controller to contact the GO Feature Flag relay proxy.
     *
     * @param options - options to initialise the controller
     * @throws InvalidOptions - if the options are invalid
     */
    @Builder
    private GoFeatureFlagController(final GoFeatureFlagProviderOptions options) throws InvalidOptions {
        this.apiKey = options.getApiKey();

        this.parsedEndpoint = HttpUrl.parse(options.getEndpoint());
        if (this.parsedEndpoint == null) {
            throw new InvalidEndpoint();
        }

        // Register JavaTimeModule to be able to deserialized java.time.Instant Object
        requestMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        requestMapper.enable(SerializationFeature.INDENT_OUTPUT);
        requestMapper.registerModule(new JavaTimeModule());

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

    /**
     * evaluateFlag is calling the GO Feature Flag relay proxy to get the evaluation of a flag.
     *
     * @param key               - name of the flag
     * @param defaultValue      - default value
     * @param evaluationContext - context of the evaluation
     * @param expectedType      - expected type of the flag
     * @param <T>               - type of the flag
     * @return EvaluationResponse with the evaluation of the flag
     * @throws OpenFeatureError - if an error occurred while evaluating the flag
     */
    public <T> EvaluationResponse<T> evaluateFlag(
            String key, T defaultValue, EvaluationContext evaluationContext, Class<?> expectedType
    ) throws OpenFeatureError {
        try {
            GoFeatureFlagUser user = GoFeatureFlagUser.fromEvaluationContext(evaluationContext);
            GoFeatureFlagRequest<T> goffRequest = new GoFeatureFlagRequest<>(user, defaultValue);

            HttpUrl url = this.parsedEndpoint.newBuilder()
                    .addEncodedPathSegment("v1")
                    .addEncodedPathSegment("feature")
                    .addEncodedPathSegment(key)
                    .addEncodedPathSegment("eval")
                    .build();

            Request.Builder reqBuilder = new Request.Builder()
                    .url(url)
                    .addHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                    .post(RequestBody.create(
                            requestMapper.writeValueAsBytes(goffRequest),
                            MediaType.get("application/json; charset=utf-8")));

            if (this.apiKey != null && !"".equals(this.apiKey)) {
                reqBuilder.addHeader(HttpHeaders.AUTHORIZATION, BEARER_TOKEN + this.apiKey);
            }

            try (Response response = this.httpClient.newCall(reqBuilder.build()).execute()) {
                if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    throw new GeneralError("invalid token used to contact GO Feature Flag relay proxy instance");
                }
                if (response.code() >= HttpURLConnection.HTTP_BAD_REQUEST) {
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
                            .providerEvaluation(providerEvaluation).cacheable(goffResp.getCacheable()).build();
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
                        .flagMetadata(MetadataUtil.convertFlagMetadata(goffResp.getMetadata()))
                        .build();

                return EvaluationResponse.<T>builder()
                        .providerEvaluation(providerEvaluation).cacheable(goffResp.getCacheable()).build();
            }
        } catch (IOException e) {
            throw new GeneralError("unknown error while retrieving flag " + key, e);
        }
    }


    /**
     * sendEventToDataCollector is calling the GO Feature Flag data/collector api to store the flag usage for analytics.
     *
     * @param eventsList - list of the event to send to GO Feature Flag
     */
    public void sendEventToDataCollector(List<Event> eventsList) {
        try {
            Events events = new Events(eventsList);
            HttpUrl url = this.parsedEndpoint.newBuilder()
                    .addEncodedPathSegment("v1")
                    .addEncodedPathSegment("data")
                    .addEncodedPathSegment("collector")
                    .build();

            Request.Builder reqBuilder = new Request.Builder()
                    .url(url)
                    .addHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                    .post(RequestBody.create(
                            requestMapper.writeValueAsBytes(events),
                            MediaType.get("application/json; charset=utf-8")));

            if (this.apiKey != null && !this.apiKey.isEmpty()) {
                reqBuilder.addHeader(HttpHeaders.AUTHORIZATION, BEARER_TOKEN + this.apiKey);
            }

            try (Response response = this.httpClient.newCall(reqBuilder.build()).execute()) {
                if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    throw new GeneralError("Unauthorized");
                }
                if (response.code() >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    throw new GeneralError("Bad request: " + response.body());
                }

                if (response.code() == HttpURLConnection.HTTP_OK) {
                    log.info("Published {} events successfully: {}", eventsList.size(), response.body());
                }
            } catch (IOException e) {
                throw new GeneralError("Impossible to send the usage data to GO Feature Flag", e);
            }
        } catch (JsonProcessingException e) {
            throw new GeneralError("Impossible to convert data collector events", e);
        }
    }

    /**
     * getFlagConfigurationEtag is retrieving the ETAG of the configuration.
     *
     * @return the ETAG of the configuration
     * @throws GoFeatureFlagException if an error occurred while retrieving the ETAG
     */
    public ConfigurationChange configurationHasChanged() throws GoFeatureFlagException {
        HttpUrl url = this.parsedEndpoint.newBuilder()
                .addEncodedPathSegment("v1")
                .addEncodedPathSegment("flag")
                .addEncodedPathSegment("change")
                .build();

        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .get();

        if (this.etag != null && !this.etag.isEmpty()) {
            reqBuilder.addHeader(HttpHeaders.IF_NONE_MATCH, this.etag);
        }
        if (this.apiKey != null && !this.apiKey.isEmpty()) {
            reqBuilder.addHeader(HttpHeaders.AUTHORIZATION, BEARER_TOKEN + this.apiKey);
        }

        try (Response response = this.httpClient.newCall(reqBuilder.build()).execute()) {
            if (response.code() == HttpURLConnection.HTTP_NOT_MODIFIED) {
                return ConfigurationChange.FLAG_CONFIGURATION_NOT_CHANGED;
            }

            if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new ConfigurationChangeEndpointNotFound();
            }

            if (!response.isSuccessful()) {
                throw new ConfigurationChangeEndpointUnknownErr();
            }

            boolean isInitialConfiguration = this.etag == null;
            this.etag = response.header(HttpHeaders.ETAG);
            return isInitialConfiguration
                    ? ConfigurationChange.FLAG_CONFIGURATION_INITIALIZED
                    : ConfigurationChange.FLAG_CONFIGURATION_UPDATED;
        } catch (IOException e) {
            throw new ConfigurationChangeEndpointUnknownErr(e);
        }
    }

    /**
     * mapErrorCode is mapping the errorCode in string received by the API to our internal SDK ErrorCode enum.
     *
     * @param errorCode - string of the errorCode received from the API
     * @return an item from the enum
     */
    private ErrorCode mapErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isEmpty()) {
            return null;
        }
        
        try {
            return ErrorCode.valueOf(errorCode);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
            if (value.getClass() == Integer.class && expectedType == Double.class) {
                return (T) Double.valueOf((Integer) value);
            }
            return (T) value;
        }
        return (T) objectToValue(value);
    }

    /**
     * DO NOT REMOVE, spotbugs: CT_CONSTRUCTOR_THROW.
     *
     * @deprecated (Used to avoid the warning of spotbugs, but it is not recommended to use it)
     */
    @Deprecated
    protected final void finalize() {
        // DO NOT REMOVE, spotbugs: CT_CONSTRUCTOR_THROW
    }
}
