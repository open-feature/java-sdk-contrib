package dev.openfeature.contrib.providers.gofeatureflag.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;

/**
 * Const is a utility class that contains constants used in the GoFeatureFlag provider.
 */
public class Const {
    // HTTP
    public static final String BEARER_TOKEN = "Bearer ";
    public static final String APPLICATION_JSON = "application/json";
    public static final String HTTP_HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HTTP_HEADER_AUTHORIZATION = "Authorization";
    public static final String HTTP_HEADER_ETAG = "ETag";
    public static final String HTTP_HEADER_IF_NONE_MATCH = "If-None-Match";
    public static final String HTTP_HEADER_LAST_MODIFIED = "Last-Modified";
    // DEFAULT VALUES
    public static final long DEFAULT_POLLING_CONFIG_FLAG_CHANGE_INTERVAL_MS = 2L * 60L * 1000L;
    public static final long DEFAULT_FLUSH_INTERVAL_MS = Duration.ofMinutes(1).toMillis();
    public static final int DEFAULT_MAX_PENDING_EVENTS = 10000;
    // MAPPERS
    public static final ObjectMapper DESERIALIZE_OBJECT_MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    public static final ObjectMapper SERIALIZE_OBJECT_MAPPER = new ObjectMapper();
    public static final ObjectMapper SERIALIZE_WASM_MAPPER =
            new ObjectMapper().setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
}
