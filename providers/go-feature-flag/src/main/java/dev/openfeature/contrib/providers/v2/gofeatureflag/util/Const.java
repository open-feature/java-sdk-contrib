package dev.openfeature.contrib.providers.v2.gofeatureflag.util;

public class Const {
    public static final String BEARER_TOKEN = "Bearer ";
    public static final String APPLICATION_JSON = "application/json";
    public static final String HTTP_HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HTTP_HEADER_AUTHORIZATION = "Authorization";
    public static final String HTTP_HEADER_ETAG = "ETag";
    public static final String HTTP_HEADER_IF_NONE_MATCH = "If-None-Match";
    public static final long DEFAULT_POLLING_CONFIG_FLAG_CHANGE_INTERVAL_MS = 2L * 60L * 1000L;
}
