package dev.openfeature.contrib.providers.ofrep.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.net.HttpURLConnection;
import java.net.ProxySelector;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolver class that interacts with the OFREP API to resolve feature flags.
 */
@Slf4j
public class Resolver {

    private final String baseUrl;
    private final ImmutableMap<String, ImmutableList<String>> headers;
    private final OfrepApi ofrepApi;

    /**
     * Constructs a Resolver with the specified base URL, headers, timeout, proxySelector and executor.
     *
     * @param baseUrl           - The base URL of the OFREP server.
     * @param headers           - The headers to include in the requests.
     * @param timeout           - The timeout for requests in seconds.
     * @param proxySelector     - The ProxySelector to use for HTTP requests.
     * @param executor          - The Executor to use for operations.
     */
    public Resolver(
            String baseUrl,
            ImmutableMap<String, ImmutableList<String>> headers,
            Duration timeout,
            ProxySelector proxySelector,
            Executor executor) {
        this.baseUrl = baseUrl;
        this.headers = headers;
        this.ofrepApi = new OfrepApi(timeout, proxySelector, executor);
    }

    private <T> ProviderEvaluation<T> resolve(Class<T> type, String key, T defaultValue, EvaluationContext ctx) {
        try {
            OfrepRequest ofrepRequest = new OfrepRequest(ImmutableMap.copyOf(ctx.asObjectMap()));
            Resolution resolution = ofrepApi.resolve(this.baseUrl, this.headers, key, ofrepRequest);

            int responseStatus = resolution.getResponseStatus();
            HttpHeaders responseHeaders = resolution.getHeaders();
            OfrepResponse responseBody = resolution.getResponse();

            Map<String, Object> metadata = responseBody.getMetadata();
            ImmutableMetadata immutableMetadata = convertToImmutableMetadata(metadata);

            switch (responseStatus) {
                case HttpURLConnection.HTTP_OK:
                    return handleResolved(key, defaultValue, type, responseBody, immutableMetadata);
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                case HttpURLConnection.HTTP_FORBIDDEN:
                    return handleGeneralError(
                            defaultValue, immutableMetadata, "authentication/authorization error for flag: " + key);
                case HttpURLConnection.HTTP_BAD_REQUEST:
                case HttpURLConnection.HTTP_NOT_ACCEPTABLE:
                    return handleInvalidContext(key, defaultValue, immutableMetadata);
                case HttpURLConnection.HTTP_NOT_FOUND:
                    return handleFlagNotFound(key, defaultValue, immutableMetadata);
                case 429:
                    String retryAfter =
                            responseHeaders.firstValue("Retry-After").orElse(null);
                    Instant retryAfterInstant = parseRetryAfter(retryAfter);
                    ofrepApi.setNextAllowedRequestTime(retryAfterInstant);
                    return handleGeneralError(
                            defaultValue,
                            immutableMetadata,
                            "Rate limit exceeded for flag: " + key + ", retry after: " + retryAfterInstant);
                default:
                    return handleGeneralError(
                            defaultValue,
                            immutableMetadata,
                            "Unknown error while retrieving flag: " + key + ", status code: " + responseStatus);
            }
        } catch (GeneralError e) {
            String errorMessage = "general error for flag: " + key + "; " + e.getMessage();
            return handleGeneralError(defaultValue, ImmutableMetadata.builder().build(), errorMessage);
        }
    }

    public ProviderEvaluation<Boolean> resolveBoolean(String key, Boolean defaultValue, EvaluationContext ctx) {
        return resolve(Boolean.class, key, defaultValue, ctx);
    }

    public ProviderEvaluation<String> resolveString(String key, String defaultValue, EvaluationContext ctx) {
        return resolve(String.class, key, defaultValue, ctx);
    }

    public ProviderEvaluation<Integer> resolveInteger(String key, Integer defaultValue, EvaluationContext ctx) {
        return resolve(Integer.class, key, defaultValue, ctx);
    }

    public ProviderEvaluation<Double> resolveDouble(String key, Double defaultValue, EvaluationContext ctx) {
        return resolve(Double.class, key, defaultValue, ctx);
    }

    /**
     * Resolves an object value for the given key.
     *
     * @param key           - The flag key to evaluate.
     * @param defaultValue  - The default value to return if there is an error.
     * @param ctx           - The evaluation context containing additional information.
     *
     * @return A ProviderEvaluation containing the resolved value, variant,
     *      reason, and metadata.
     */
    public ProviderEvaluation<Value> resolveObject(String key, Value defaultValue, EvaluationContext ctx) {
        ProviderEvaluation<Object> evaluation = resolve(Object.class, key, defaultValue, ctx);

        return ProviderEvaluation.<Value>builder()
                .value(Value.objectToValue(evaluation.getValue()))
                .variant(evaluation.getVariant())
                .reason(evaluation.getReason())
                .errorCode(evaluation.getErrorCode())
                .errorMessage(evaluation.getErrorMessage())
                .flagMetadata(evaluation.getFlagMetadata())
                .build();
    }

    private ImmutableMetadata convertToImmutableMetadata(Map<String, Object> metadata) {
        ImmutableMetadata.ImmutableMetadataBuilder builder = ImmutableMetadata.builder();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                log.warn("Null value for key: {}, skipping", key);
                continue;
            }

            if (value instanceof String) {
                builder.addString(key, (String) value);
            } else if (value instanceof Integer) {
                builder.addInteger(key, (Integer) value);
            } else if (value instanceof Long) {
                builder.addLong(key, (Long) value);
            } else if (value instanceof Float) {
                builder.addFloat(key, (Float) value);
            } else if (value instanceof Double) {
                builder.addDouble(key, (Double) value);
            } else if (value instanceof Boolean) {
                builder.addBoolean(key, (Boolean) value);
            } else {
                log.warn("Unsupported metadata type for key: {}, value: {}", key, value);
                builder.addString(key, value.toString());
            }
        }
        return builder.build();
    }

    private <T> ProviderEvaluation<T> handleResolved(
            String key, T defaultValue, Class<T> type, OfrepResponse response, ImmutableMetadata metadata) {

        Object responseValue = response.getValue();

        if (responseValue == null) {
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .errorMessage("No value returned for flag: " + key)
                    .flagMetadata(metadata)
                    .build();
        }

        if (!type.isInstance(responseValue)) {
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .errorCode(ErrorCode.TYPE_MISMATCH)
                    .errorMessage("Type mismatch: expected " + type.getSimpleName() + " but got "
                            + responseValue.getClass().getSimpleName())
                    .flagMetadata(metadata)
                    .build();
        }

        return ProviderEvaluation.<T>builder()
                .value(type.cast(responseValue))
                .reason(response.getReason())
                .variant(response.getVariant())
                .flagMetadata(metadata)
                .build();
    }

    private <T> ProviderEvaluation<T> handleFlagNotFound(String key, T defaultValue, ImmutableMetadata metadata) {
        return ProviderEvaluation.<T>builder()
                .value(defaultValue)
                .errorMessage("flag: " + key + " not found")
                .errorCode(ErrorCode.FLAG_NOT_FOUND)
                .flagMetadata(metadata)
                .build();
    }

    private <T> ProviderEvaluation<T> handleInvalidContext(String key, T defaultValue, ImmutableMetadata metadata) {
        return ProviderEvaluation.<T>builder()
                .value(defaultValue)
                .errorMessage("invalid context for flag: " + key)
                .errorCode(ErrorCode.INVALID_CONTEXT)
                .flagMetadata(metadata)
                .build();
    }

    private <T> ProviderEvaluation<T> handleGeneralError(
            T defaultValue, ImmutableMetadata metadata, String errorMessage) {
        return ProviderEvaluation.<T>builder()
                .value(defaultValue)
                .errorMessage(errorMessage)
                .errorCode(ErrorCode.GENERAL)
                .flagMetadata(metadata)
                .build();
    }

    private static Instant parseRetryAfter(String retryAfter) {
        if (retryAfter == null || retryAfter.isEmpty()) {
            return Instant.now();
        }

        try {
            long seconds = Long.parseLong(retryAfter);
            return Instant.now().plusSeconds(seconds);
        } catch (NumberFormatException numberFormatException) {
            try {
                DateTimeFormatter rfc1123Formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
                ZonedDateTime zonedDateTime = ZonedDateTime.parse(retryAfter, rfc1123Formatter);
                return zonedDateTime.toInstant();
            } catch (Exception e) {
                log.error("Failed to parse Retry-After header: ", e);
                return Instant.now();
            }
        }
    }
}
