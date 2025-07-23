package dev.openfeature.contrib.providers.ofrep.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ParseError;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

/**
 * The OfrepApi class is responsible for communicating with the OFREP server to evaluate flags.
 */
@Slf4j
public class OfrepApi {

    private static final String path = "/ofrep/v1/evaluate/flags/";
    private final ObjectMapper serializer;
    private final ObjectMapper deserializer;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private Instant nextAllowedRequestTime = Instant.now();

    /**
     * Constructs an OfrepApi instance with a HTTP client and JSON serializers.
     *
     * @param requestTimeout - The request timeout duration for the request.
     * @param connectTimeout - The connect timeout duration for establishing HTTP connection.
     * @param proxySelector  - The ProxySelector to use for HTTP requests.
     * @param executor       - The Executor to use for operations.
     */
    public OfrepApi(Duration requestTimeout, Duration connectTimeout, ProxySelector proxySelector, Executor executor) {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .proxy(proxySelector)
                .executor(executor)
                .build();
        this.requestTimeout = requestTimeout;
        serializer = new ObjectMapper();
        deserializer = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * prepareHttpRequest is preparing the request to be sent to the OFREP Server.
     *
     * @param url         - url of the request
     * @param headers     - headers to be included in the request
     * @param requestBody - body of the request
     *
     * @return HttpRequest ready to be sent
     * @throws JsonProcessingException - if an error occurred while processing the json.
     */
    private <T> HttpRequest prepareHttpRequest(
            final URI url, ImmutableMap<String, ImmutableList<String>> headers, final T requestBody)
            throws JsonProcessingException {

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(url)
                .timeout(this.requestTimeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofByteArray(serializer.writeValueAsBytes(requestBody)));

        for (ImmutableMap.Entry<String, ImmutableList<String>> entry : headers.entrySet()) {
            String key = entry.getKey();
            for (String value : entry.getValue()) {
                reqBuilder.header(key, value);
            }
        }

        return reqBuilder.build();
    }

    /**
     * resolve is the method that interacts with the OFREP server to evaluate flags.
     *
     * @param baseUrl      - The base URL of the OFREP server.
     * @param headers      - headers to include in the request.
     * @param key          - The flag key to evaluate.
     * @param requestBody  - The evaluation context as a map of key-value pairs.
     *
     * @return Resolution object, containing the response status, headers, and body.
     */
    public Resolution resolve(
            String baseUrl,
            ImmutableMap<String, ImmutableList<String>> headers,
            String key,
            final OfrepRequest requestBody) {
        if (nextAllowedRequestTime.isAfter(Instant.now())) {
            throw new GeneralError("Rate limit exceeded. Please wait before making another request.");
        }

        try {
            String fullPath = baseUrl + path + key;
            URI uri = URI.create(fullPath);

            HttpRequest request = prepareHttpRequest(uri, headers, requestBody);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            OfrepResponse responseBody = deserializer.readValue(response.body(), OfrepResponse.class);

            return new Resolution(response.statusCode(), response.headers(), responseBody);
        } catch (JsonProcessingException e) {
            throw new ParseError("Error processing JSON: " + e.getMessage());
        } catch (IOException e) {
            throw new GeneralError("IO error: " + e.getMessage());
        } catch (InterruptedException e) {
            throw new GeneralError("Request interrupted: " + e.getMessage());
        }
    }

    /**
     * Sets the next allowed request time based on the Retry-After header.
     * If the provided time is later than the current next allowed request time, it updates it.
     *
     * @param retryAfter The value of the Retry-After header, which can be a number of seconds or a date string.
     */
    public void setNextAllowedRequestTime(Instant retryAfter) {
        if (retryAfter.isAfter(nextAllowedRequestTime)) {
            nextAllowedRequestTime = retryAfter;
        }
    }
}
