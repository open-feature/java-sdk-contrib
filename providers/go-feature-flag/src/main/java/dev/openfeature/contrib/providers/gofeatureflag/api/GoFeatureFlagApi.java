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
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.InvalidContextError;
import dev.openfeature.sdk.exceptions.OpenFeatureError;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * GoFeatureFlagApi is the class to contact the GO Feature Flag relay proxy.
 */
@Slf4j
public final class GoFeatureFlagApi {
    /** apiKey contains the token to use while calling GO Feature Flag relay proxy. */
    private final String apiKey;

    /** httpClient is the instance of the HttpClient used by the provider. */
    private final HttpClient httpClient;

    /** endpoint is the endpoint of the GO Feature Flag relay proxy. */
    private final URI endpoint;

    /** timeout is the timeout in milliseconds for the HTTP requests. */
    private final int timeout;

    /**
     * GoFeatureFlagController is the constructor of the controller to contact the GO Feature Flag
     * relay proxy.
     *
     * @param options - options to initialise the controller
     * @throws InvalidOptions - if the options are invalid
     */
    @Builder
    private GoFeatureFlagApi(final GoFeatureFlagProviderOptions options) throws InvalidOptions {
        if (options == null) {
            throw new InvalidOptions("No options provided");
        }
        options.validate();
        this.apiKey = options.getApiKey();

        try {
            this.endpoint = new URI(options.getEndpoint());
        } catch (URISyntaxException e) {
            throw new InvalidEndpoint(e);
        }

        // Register JavaTimeModule to be able to deserialized java.time.Instant Object
        Const.SERIALIZE_OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Const.SERIALIZE_OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
        Const.SERIALIZE_OBJECT_MAPPER.registerModule(new JavaTimeModule());

        timeout = options.getTimeout() == 0 ? 10000 : options.getTimeout();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
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
        return this.evaluateFlag(key, evaluationContext, 0);
    }

    /**
     * evaluateFlag is calling the GO Feature Flag relay proxy to evaluate the feature flag.\
     * It will retry once if the relay proxy is unavailable.
     *
     * @param key               - name of the flag
     * @param evaluationContext - context of the evaluation
     * @param retryCount        - number of retries already done
     * @return EvaluationResponse with the evaluation of the flag
     * @throws OpenFeatureError - if an error occurred while evaluating the flag
     */
    private GoFeatureFlagResponse evaluateFlag(
            final String key, final EvaluationContext evaluationContext, final int retryCount) throws OpenFeatureError {
        try {
            URI url = this.endpoint.resolve("/ofrep/v1/evaluate/flags/" + key);

            val requestBody = OfrepRequest.builder()
                    .context(evaluationContext.asObjectMap())
                    .build();

            HttpRequest request = prepareHttpRequest(url, requestBody);

            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            switch (response.statusCode()) {
                case HttpURLConnection.HTTP_OK:
                    val goffResp = Const.DESERIALIZE_OBJECT_MAPPER.readValue(body, OfrepResponse.class);
                    return goffResp.toGoFeatureFlagResponse();
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                case HttpURLConnection.HTTP_FORBIDDEN:
                    throw new GeneralError("authentication/authorization error");
                case HttpURLConnection.HTTP_BAD_REQUEST:
                    throw new InvalidContextError("Invalid context: " + body);
                case HttpURLConnection.HTTP_UNAVAILABLE:
                    // If the relay proxy is unavailable, we can retry once.
                    if (retryCount < 1) {
                        log.warn("GO Feature Flag relay proxy is unavailable, retrying evaluation for flag: {}", key);
                        return this.evaluateFlag(key, evaluationContext, retryCount + 1);
                    }
                    throw new GeneralError("Service Unavailable: " + body);
                case HttpURLConnection.HTTP_NOT_FOUND:
                    throw new FlagNotFoundError("Flag " + key + " not found");
                default:
                    throw new GeneralError("Unknown error while retrieving flag " + body);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
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
            final URI url = this.endpoint.resolve("/v1/flag/configuration");

            HttpRequest.Builder reqBuilder =
                    HttpRequest.newBuilder().uri(url).header(Const.HTTP_HEADER_CONTENT_TYPE, Const.APPLICATION_JSON);

            if (this.apiKey != null && !this.apiKey.isEmpty()) {
                reqBuilder.header(Const.HTTP_HEADER_AUTHORIZATION, Const.BEARER_TOKEN + this.apiKey);
            }

            if (etag != null && !etag.isEmpty()) {
                reqBuilder.header(Const.HTTP_HEADER_IF_NONE_MATCH, etag);
            }

            reqBuilder.POST(
                    HttpRequest.BodyPublishers.ofByteArray(Const.SERIALIZE_OBJECT_MAPPER.writeValueAsBytes(request)));

            HttpResponse<String> response =
                    this.httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            switch (response.statusCode()) {
                case HttpURLConnection.HTTP_OK:
                case HttpURLConnection.HTTP_NOT_MODIFIED:
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
        } catch (final JsonProcessingException e) {
            throw new ImpossibleToRetrieveConfiguration("retrieve flag configuration error", e);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ImpossibleToRetrieveConfiguration("retrieve flag configuration error", e);
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
            URI url = this.endpoint.resolve("/v1/data/collector");

            HttpRequest request = prepareHttpRequest(url, requestBody);

            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            switch (response.statusCode()) {
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
        } catch (final IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ImpossibleToSendEventsException("Error while sending data for relay-proxy exporter", e);
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
    private FlagConfigResponse handleFlagConfigurationSuccess(final HttpResponse<String> response, final String body)
            throws JsonProcessingException {
        var result = FlagConfigResponse.builder()
                .etag(response.headers().firstValue(Const.HTTP_HEADER_ETAG).orElse(null))
                .lastUpdated(extractLastUpdatedFromHeaders(response))
                .build();

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            val goffResp = Const.DESERIALIZE_OBJECT_MAPPER.readValue(body, FlagConfigApiResponse.class);
            result.setFlags(goffResp.getFlags());
            result.setEvaluationContextEnrichment(goffResp.getEvaluationContextEnrichment());
        }

        return result;
    }

    /**
     * extractLastUpdatedFromHeaders is extracting the Last-Modified header from the response.
     *
     * @param response - the HTTP response
     * @return Date - the parsed Last-Modified date, or null if not present or parsing fails
     */
    private Date extractLastUpdatedFromHeaders(final HttpResponse<String> response) {
        try {
            String headerValue = response.headers()
                    .firstValue(Const.HTTP_HEADER_LAST_MODIFIED)
                    .orElse(null);
            SimpleDateFormat lastModifiedHeaderFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
            return headerValue != null ? lastModifiedHeaderFormatter.parse(headerValue) : null;
        } catch (Exception e) {
            log.debug("Error parsing Last-Modified header: {}", e.getMessage());
            return null;
        }
    }

    /**
     * prepareHttpRequest is preparing the request to be sent to the GO Feature Flag relay proxy.
     *
     * @param url         - url of the request
     * @param requestBody - body of the request
     * @return HttpRequest ready to be sent
     * @throws JsonProcessingException - if an error occurred while processing the json
     */
    private <T> HttpRequest prepareHttpRequest(final URI url, final T requestBody) throws JsonProcessingException {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(url)
                .timeout(Duration.ofMillis(timeout))
                .header(Const.HTTP_HEADER_CONTENT_TYPE, Const.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                        Const.SERIALIZE_OBJECT_MAPPER.writeValueAsBytes(requestBody)));

        if (this.apiKey != null && !this.apiKey.isEmpty()) {
            reqBuilder.header(Const.HTTP_HEADER_AUTHORIZATION, Const.BEARER_TOKEN + this.apiKey);
        }

        return reqBuilder.build();
    }
}
