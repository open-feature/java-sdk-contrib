package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.CacheType;

/**
 * Helper class to hold configuration default values.
 */
public final class Config {
    static final Evaluator DEFAULT_RESOLVER_TYPE = Evaluator.RPC;
    static final String DEFAULT_PORT = "8013";
    static final String DEFAULT_TLS = "false";
    static final String DEFAULT_HOST = "localhost";

    static final int DEFAULT_DEADLINE = 500;
    static final int DEFAULT_MAX_CACHE_SIZE = 1000;

    static final String HOST_ENV_VAR_NAME = "FLAGD_HOST";
    static final String PORT_ENV_VAR_NAME = "FLAGD_PORT";
    static final String TLS_ENV_VAR_NAME = "FLAGD_TLS";
    static final String SOCKET_PATH_ENV_VAR_NAME = "FLAGD_SOCKET_PATH";
    static final String SERVER_CERT_PATH_ENV_VAR_NAME = "FLAGD_SERVER_CERT_PATH";
    static final String CACHE_ENV_VAR_NAME = "FLAGD_CACHE";
    static final String MAX_CACHE_SIZE_ENV_VAR_NAME = "FLAGD_MAX_CACHE_SIZE";
    static final String MAX_EVENT_STREAM_RETRIES_ENV_VAR_NAME = "FLAGD_MAX_EVENT_STREAM_RETRIES";
    static final String BASE_EVENT_STREAM_RETRY_BACKOFF_MS_ENV_VAR_NAME = "FLAGD_RETRY_BACKOFF_MS";
    static final String DEADLINE_MS_ENV_VAR_NAME = "FLAGD_DEADLINE_MS";
    static final String SOURCE_SELECTOR_ENV_VAR_NAME = "FLAGD_SOURCE_SELECTOR";

    public static final String STATIC_REASON = "STATIC";
    public static final String CACHED_REASON = "CACHED";

    public static final String FLAG_KEY_FIELD = "flag_key";
    public static final String CONTEXT_FIELD = "context";
    public static final String VARIANT_FIELD = "variant";
    public static final String VALUE_FIELD = "value";
    public static final String REASON_FIELD = "reason";
    public static final String METADATA_FIELD = "metadata";

    public static final String LRU_CACHE = CacheType.LRU.getValue();
    static final String DEFAULT_CACHE = LRU_CACHE;

    static final int DEFAULT_MAX_EVENT_STREAM_RETRIES = 5;
    static final int BASE_EVENT_STREAM_RETRY_BACKOFF_MS = 1000;

    static String fallBackToEnvOrDefault(String key, String defaultValue) {
        return System.getenv(key) != null ? System.getenv(key) : defaultValue;
    }

    static int fallBackToEnvOrDefault(String key, int defaultValue) {
        try {
            return System.getenv(key) != null ? Integer.parseInt(System.getenv(key)) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * flagd evaluator type.
     */
    public enum Evaluator {
        /**
         * This is the default resolver type, which connects to flagd instance with flag evaluation gRPC contract.
         * Evaluations are performed remotely.
         */
        RPC,
        /**
         * This is the in-process resolving type, where flags are fetched with flag sync gRPC contract and stored
         * locally for in-process evaluation.
         * Evaluations are preformed in-process.
         */
        IN_PROCESS
    }
}
