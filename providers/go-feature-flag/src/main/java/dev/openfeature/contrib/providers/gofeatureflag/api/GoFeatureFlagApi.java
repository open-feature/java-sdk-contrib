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
import dev.openfeature.contrib.providers.gofeatureflag.exception.AuthenticationFailure;
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
            val rawEndpoint = options.getEndpoint();
            this.endpoint = new URI(rawEndpoint.endsWith("/") ? rawEndpoint : rawEndpoint + "/");
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
            URI url = route(Const.PATH_OFREP_EVALUATE + encodePathSegment(key));

            val requestBody = OfrepRequest.builder()
                    .context(evaluationContext.asObjectMap())
                    .build();

            HttpResponse<String> response =
                    this.httpClient.send(prepareHttpRequest(url, requestBody), HttpResponse.BodyHandlers.ofString());
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
     * <p>A {@code 304 Not Modified} response is reported as an empty Optional rather than as an
     * empty configuration object, so that the not-modified branch is structurally incapable of
     * carrying a configuration and cannot be mistaken for one downstream.</p>
     *
     * @param etag  - etag of the request
     * @param flags - flags to retrieve, empty for all of them
     * @return the flag configuration, or empty if the configuration has not been modified
     */
    public Optional<FlagConfigResponse> retrieveFlagConfiguration(final String etag, final List<String> flags) {
        try {
            val request = new FlagConfigApiRequest(flags == null ? Collections.emptyList() : flags);
            final URI url = route(Const.PATH_FLAG_CONFIGURATION);

            final HttpRequest httpRequest = etag != null && !etag.isEmpty()
                    ? prepareHttpRequest(url, request, Const.HTTP_HEADER_IF_NONE_MATCH, etag)
                    : prepareHttpRequest(url, request);

            HttpResponse<String> response = this.httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            switch (response.statusCode()) {
                case HttpURLConnection.HTTP_OK:
                    return Optional.of(handleFlagConfigurationSuccess(response, body));
                case HttpURLConnection.HTTP_NOT_MODIFIED:
                    return Optional.empty();
                case HttpURLConnection.HTTP_NOT_FOUND:
                    throw new FlagConfigurationEndpointNotFound();
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                case HttpURLConnection.HTTP_FORBIDDEN:
                    throw new AuthenticationFailure(
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
     * handleFlagConfigurationSuccess is handling the 200 response of the flag configuration request.
     * It is never reached for a 304, which carries no configuration.
     *
     * @param response - response of the request
     * @param body     - body of the request
     * @return FlagConfigResponse with the flag configuration
     * @throws JsonProcessingException           - if an error occurred while processing the json
     * @throws ImpossibleToRetrieveConfiguration - if the response carries no flag map
     */
    private FlagConfigResponse handleFlagConfigurationSuccess(final HttpResponse<String> response, final String body)
            throws JsonProcessingException {
        val goffResp = Const.DESERIALIZE_OBJECT_MAPPER.readValue(body, FlagConfigApiResponse.class);

        // A 200 that decodes to no flag map is a failed refresh, not an empty configuration:
        // accepting it would wipe every flag and advance the ETag, making the empty state permanent.
        // A null evaluationContextEnrichment is NOT the same case - the relay proxy builds that field
        // from a Go map and a nil map marshals to null - so it is accepted as "no enrichment".
        if (goffResp == null || goffResp.getFlags() == null) {
            throw new ImpossibleToRetrieveConfiguration(
                    "retrieve flag configuration error: the response contains no flag map");
        }

        return FlagConfigResponse.builder()
                .etag(response.headers().firstValue(Const.HTTP_HEADER_ETAG).orElse(null))
                .lastUpdated(extractLastUpdatedFromHeaders(response))
                .flags(goffResp.getFlags())
                .evaluationContextEnrichment(goffResp.getEvaluationContextEnrichment())
                .build();
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
            SimpleDateFormat lastModifiedHeaderFormatter =
                    new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
            return headerValue != null ? lastModifiedHeaderFormatter.parse(headerValue) : null;
        } catch (Exception e) {
            log.debug("Error parsing Last-Modified header: {}", e.getMessage());
            return null;
        }
    }

    /**
     * encodePathSegment escapes a flag key so that it cannot alter the shape of the URL. A key
     * containing a space would otherwise make the URI unparseable, and one containing a slash would
     * address a different route.
     *
     * @param segment - the raw flag key
     * @return the key, safe to place in a path
     */
    private static String encodePathSegment(final String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * route builds the URL of an API route from the configured endpoint, preserving any path prefix
     * the endpoint carries. Resolving an absolute path such as {@code /v1/flag/configuration} would
     * discard that prefix (RFC 3986 section 5.3) and silently retarget the request.
     *
     * @param path - route path, relative to the endpoint and without a leading slash
     * @return the URL to call
     */
    private URI route(final String path) {
        return this.endpoint.resolve(path.startsWith("/") ? path.substring(1) : path);
    }

    /**
     * prepareHttpRequest is preparing the request to be sent to the GO Feature Flag relay proxy.
     *
     * @param url         - url of the request
     * @param requestBody - body of the request
     * @return HttpRequest ready to be sent
     * @throws JsonProcessingException - if an error occurred while processing the json
     */
    private <T> HttpRequest prepareHttpRequest(final URI url, final T requestBody, final String... customHeaders)
            throws JsonProcessingException {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(url)
                .timeout(Duration.ofMillis(timeout))
                .header(Const.HTTP_HEADER_CONTENT_TYPE, Const.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                        Const.SERIALIZE_OBJECT_MAPPER.writeValueAsBytes(requestBody)));

        if (customHeaders != null && customHeaders.length > 0) {
            reqBuilder.headers(customHeaders);
        }

        if (this.apiKey != null && !this.apiKey.isEmpty()) {
            reqBuilder.header(Const.HTTP_HEADER_API_KEY, this.apiKey);
        }
        return reqBuilder.build();
    }
}
