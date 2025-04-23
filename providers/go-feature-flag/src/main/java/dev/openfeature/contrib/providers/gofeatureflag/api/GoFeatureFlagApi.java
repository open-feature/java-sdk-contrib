package dev.openfeature.contrib.providers.gofeatureflag.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.api.bean.ExporterRequest;
import dev.openfeature.contrib.providers.gofeatureflag.api.bean.FlagConfigApiRequest;
import dev.openfeature.contrib.providers.gofeatureflag.api.bean.FlagConfigApiResponse;
import dev.openfeature.contrib.providers.gofeatureflag.api.bean.OfrepRequest;
import dev.openfeature.contrib.providers.gofeatureflag.api.bean.OfrepResponse;
import dev.openfeature.contrib.providers.gofeatureflag.bean.FlagConfigResponse;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.bean.IEvent;
import dev.openfeature.contrib.providers.gofeatureflag.exception.FlagConfigurationEndpointNotFound;
import dev.openfeature.contrib.providers.gofeatureflag.exception.ImpossibleToRetrieveConfiguration;
import dev.openfeature.contrib.providers.gofeatureflag.exception.ImpossibleToSendEventsException;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.validator.Validator;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.InvalidContextError;
import dev.openfeature.sdk.exceptions.OpenFeatureError;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class GoFeatureFlagApi {
    /** apiKey contains the token to use while calling GO Feature Flag relay proxy. */
    private final String apiKey;

    /** httpClient is the instance of the OkHttpClient used by the provider. */
    private final OkHttpClient httpClient;

    /** parsedEndpoint is the endpoint of the GO Feature Flag relay proxy. */
    private final HttpUrl parsedEndpoint;


    /**
     * GoFeatureFlagController is the constructor of the controller to contact the GO Feature Flag
     * relay proxy.
     *
     * @param options - options to initialise the controller
     * @throws InvalidOptions - if the options are invalid
     */
    @Builder
    private GoFeatureFlagApi(final GoFeatureFlagProviderOptions options) throws InvalidOptions {
        Validator.ProviderOptions(options);
        this.apiKey = options.getApiKey();
        this.parsedEndpoint = HttpUrl.parse(options.getEndpoint());
        if (this.parsedEndpoint == null) {
            throw new InvalidEndpoint();
        }

        // Register JavaTimeModule to be able to deserialized java.time.Instant Object
        Const.SERIALIZE_OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Const.SERIALIZE_OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
        Const.SERIALIZE_OBJECT_MAPPER.registerModule(new JavaTimeModule());

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
     * evaluateFlag is calling the GO Feature Flag relay proxy to evaluate the feature flag.
     *
     * @param key               - name of the flag
     * @param evaluationContext - context of the evaluation
     * @return EvaluationResponse with the evaluation of the flag
     * @throws OpenFeatureError - if an error occurred while evaluating the flag
     */
    public GoFeatureFlagResponse evaluateFlag(final String key, final EvaluationContext evaluationContext)
            throws OpenFeatureError {
        try {
            HttpUrl url = this.parsedEndpoint
                    .newBuilder()
                    .addEncodedPathSegment("ofrep")
                    .addEncodedPathSegment("v1")
                    .addEncodedPathSegment("evaluate")
                    .addEncodedPathSegment("flags")
                    .addEncodedPathSegment(key)
                    .build();

            val requestBody = OfrepRequest.builder().context(evaluationContext.asObjectMap()).build();
            val reqBuilder = prepareHttpRequest(url, requestBody);
            try (Response response = this.httpClient.newCall(reqBuilder.build()).execute()) {
                val responseBody = response.body();
                String body = responseBody != null ? responseBody.string() : "";

                switch (response.code()) {
                    case HttpURLConnection.HTTP_OK:
                        val goffResp = Const.DESERIALIZE_OBJECT_MAPPER.readValue(body, OfrepResponse.class);
                        return goffResp.toGoFeatureFlagResponse();
                    case HttpURLConnection.HTTP_UNAUTHORIZED:
                    case HttpURLConnection.HTTP_FORBIDDEN:
                        throw new GeneralError("authentication/authorization error");
                    case HttpURLConnection.HTTP_BAD_REQUEST:
                        throw new InvalidContextError("Invalid context: " + body);
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        throw new FlagNotFoundError("Flag " + key + " not found");
                    default:
                        throw new GeneralError("Unknown error while retrieving flag " + body);
                }
            }
        } catch (IOException e) {
            throw new GeneralError("unknown error while retrieving flag " + key, e);
        }
    }


    /**
     * retrieveFlagConfiguration is calling the GO Feature Flag relay proxy to retrieve the flags'
     * configuration.
     *
     * @param etag - etag of the request
     * @return FlagConfigResponse with the flag configuration
     */
    public FlagConfigResponse retrieveFlagConfiguration(final String etag, final List<String> flags) {
        try {
            val request = new FlagConfigApiRequest(flags == null ? Collections.emptyList() : flags);
            final HttpUrl url = this.parsedEndpoint
                    .newBuilder()
                    .addEncodedPathSegment("v1")
                    .addEncodedPathSegment("flag")
                    .addEncodedPathSegment("configuration")
                    .build();

            val reqBuilder = prepareHttpRequest(url, request);
            if (etag != null && !etag.isEmpty()) {
                reqBuilder.addHeader(Const.HTTP_HEADER_IF_NONE_MATCH, etag);
            }

            try (final Response response = this.httpClient.newCall(reqBuilder.build()).execute()) {
                val responseBody = response.body();
                String body = responseBody != null ? responseBody.string() : "";
                switch (response.code()) {
                    case HttpURLConnection.HTTP_OK:
                        return handleFlagConfigurationSuccess(response, body);
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        throw new FlagConfigurationEndpointNotFound();
                    case HttpURLConnection.HTTP_UNAUTHORIZED:
                    case HttpURLConnection.HTTP_FORBIDDEN:
                        throw new ImpossibleToRetrieveConfiguration(
                                "retrieve flag configuration error: authentication/authorization error");
                    case HttpURLConnection.HTTP_BAD_REQUEST:
                        throw new ImpossibleToRetrieveConfiguration(
                                "retrieve flag configuration error: Bad request: " + body);
                    default:
                        throw new ImpossibleToRetrieveConfiguration(
                                "retrieve flag configuration error: unexpected http code " + body);
                }
            } catch (final IOException e) {
                throw new ImpossibleToRetrieveConfiguration(
                        "retrieve flag configuration error", e);
            }
        } catch (final JsonProcessingException e) {
            throw new ImpossibleToRetrieveConfiguration(
                    "retrieve flag configuration error", e);
        }
    }

    /**
     * sendEventToDataCollector is calling the GO Feature Flag data/collector api to store the flag
     * usage for analytics.
     *
     * @param eventsList - list of the event to send to GO Feature Flag
     */
    public void sendEventToDataCollector(final List<IEvent> eventsList, final Map<String, Object> exporterMetadata) {
        try {
            ExporterRequest requestBody = new ExporterRequest(eventsList, exporterMetadata);
            HttpUrl url = this.parsedEndpoint
                    .newBuilder()
                    .addEncodedPathSegment("v1")
                    .addEncodedPathSegment("data")
                    .addEncodedPathSegment("collector")
                    .build();
            val reqBuilder = prepareHttpRequest(url, requestBody);
            try (final Response response = this.httpClient.newCall(reqBuilder.build()).execute()) {
                val responseBody = response.body();
                String body = responseBody != null ? responseBody.string() : "";
                switch (response.code()) {
                    case HttpURLConnection.HTTP_OK:
                        log.info("Published {} events successfully: {}", eventsList.size(), body);
                        break;
                    case HttpURLConnection.HTTP_UNAUTHORIZED:
                    case HttpURLConnection.HTTP_FORBIDDEN:
                        throw new GeneralError("authentication/authorization error");
                    case HttpURLConnection.HTTP_BAD_REQUEST:
                        throw new GeneralError("Bad request: " + body);
                    default:
                        throw new ImpossibleToSendEventsException(
                                String.format("Error while sending data to the relay-proxy exporter %s", body));
                }
            }
        } catch (final IOException e) {
            throw new ImpossibleToSendEventsException(
                    "Error while sending data for relay-proxy exporter", e);
        }
    }

    /**
     * handleFlagConfigurationSuccess is handling the success response of the flag configuration
     * request.
     *
     * @param response - response of the request
     * @param body     - body of the request
     * @return FlagConfigResponse with the flag configuration
     * @throws JsonProcessingException - if an error occurred while processing the json
     */
    private FlagConfigResponse handleFlagConfigurationSuccess(final Response response, final String body)
            throws JsonProcessingException {
        val goffResp = Const.DESERIALIZE_OBJECT_MAPPER.readValue(body, FlagConfigApiResponse.class);
        val etagHeader = response.header(Const.HTTP_HEADER_ETAG);
        Date lastUpdated;
        try {
            val headerValue = response.header(Const.HTTP_HEADER_LAST_MODIFIED);
            lastUpdated = headerValue != null ? Const.LAST_MODIFIED_HEADER_FORMATTER.parse(headerValue) : null;
        } catch (Exception e) {
            log.debug("Error parsing Last-Modified header: {}", e.getMessage());
            lastUpdated = null;
        }
        return FlagConfigResponse.builder()
                .flags(goffResp.getFlags())
                .etag(etagHeader)
                .evaluationContextEnrichment(goffResp.getEvaluationContextEnrichment())
                .lastUpdated(lastUpdated)
                .build();
    }

    /**
     * prepareHttpRequest is preparing the request to be sent to the GO Feature Flag relay proxy.
     *
     * @param url         - url of the request
     * @param requestBody - body of the request
     * @return Request.Builder with the request prepared
     * @throws JsonProcessingException - if an error occurred while processing the json
     */
    private <T> Request.Builder prepareHttpRequest(final HttpUrl url, final T requestBody)
            throws JsonProcessingException {
        final Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .addHeader(Const.HTTP_HEADER_CONTENT_TYPE, Const.APPLICATION_JSON)
                .post(RequestBody.create(
                        Const.SERIALIZE_OBJECT_MAPPER.writeValueAsBytes(requestBody),
                        MediaType.get("application/json; charset=utf-8")));

        if (this.apiKey != null && !this.apiKey.isEmpty()) {
            reqBuilder.addHeader(Const.HTTP_HEADER_AUTHORIZATION, Const.BEARER_TOKEN + this.apiKey);
        }

        return reqBuilder;
    }

}
