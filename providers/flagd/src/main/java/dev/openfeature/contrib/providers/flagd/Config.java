package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.contrib.providers.flagd.resolver.rpc.cache.CacheType;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/** Helper class to hold configuration default values. */
@Slf4j
public final class Config {
    static final Resolver DEFAULT_RESOLVER_TYPE = Resolver.RPC;
    static final String DEFAULT_RPC_PORT = "8013";
    static final String DEFAULT_IN_PROCESS_PORT = "8015";
    static final String DEFAULT_TLS = "false";
    static final String DEFAULT_HOST = "localhost";

    static final int DEFAULT_DEADLINE = 500;
    static final int DEFAULT_MAX_RETRY_BACKOFF_MS = 12000;
    static final int DEFAULT_STREAM_DEADLINE_MS = 10 * 60 * 1000;
    static final int DEFAULT_STREAM_RETRY_GRACE_PERIOD = 5;
    static final int DEFAULT_MAX_CACHE_SIZE = 1000;
    static final int DEFAULT_OFFLINE_POLL_MS = 5000;
    static final long DEFAULT_KEEP_ALIVE = 0;
    static final String DEFAULT_REINITIALIZE_ON_ERROR = "false";

    static final String RESOLVER_ENV_VAR = "FLAGD_RESOLVER";
    static final String HOST_ENV_VAR_NAME = "FLAGD_HOST";
    static final String PORT_ENV_VAR_NAME = "FLAGD_PORT";
    static final String SYNC_PORT_ENV_VAR_NAME = "FLAGD_SYNC_PORT";
    static final String TLS_ENV_VAR_NAME = "FLAGD_TLS";
    static final String DEFAULT_AUTHORITY_ENV_VAR_NAME = "FLAGD_DEFAULT_AUTHORITY";
    static final String SOCKET_PATH_ENV_VAR_NAME = "FLAGD_SOCKET_PATH";
    static final String SERVER_CERT_PATH_ENV_VAR_NAME = "FLAGD_SERVER_CERT_PATH";
    static final String CACHE_ENV_VAR_NAME = "FLAGD_CACHE";
    static final String MAX_CACHE_SIZE_ENV_VAR_NAME = "FLAGD_MAX_CACHE_SIZE";
    static final String BASE_EVENT_STREAM_RETRY_BACKOFF_MS_ENV_VAR_NAME = "FLAGD_RETRY_BACKOFF_MS";
    static final String DEADLINE_MS_ENV_VAR_NAME = "FLAGD_DEADLINE_MS";
    static final String FLAGD_RETRY_BACKOFF_MAX_MS_VAR_NAME = "FLAGD_RETRY_BACKOFF_MAX_MS";
    static final String STREAM_DEADLINE_MS_ENV_VAR_NAME = "FLAGD_STREAM_DEADLINE_MS";
    static final String SOURCE_SELECTOR_ENV_VAR_NAME = "FLAGD_SOURCE_SELECTOR";
    static final String FATAL_STATUS_CODES_ENV_VAR_NAME = "FLAGD_FATAL_STATUS_CODES";
    /**
     * Environment variable to fetch Provider id.
     *
     * @deprecated please use {@link #PROVIDER_ID_ENV_VAR_NAME}
     */
    @Deprecated(forRemoval = true)
    static final String SOURCE_PROVIDER_ID_ENV_VAR_NAME = "FLAGD_SOURCE_PROVIDER_ID";

    static final String PROVIDER_ID_ENV_VAR_NAME = "FLAGD_PROVIDER_ID";
    static final String OFFLINE_SOURCE_PATH = "FLAGD_OFFLINE_FLAG_SOURCE_PATH";
    static final String OFFLINE_POLL_MS = "FLAGD_OFFLINE_POLL_MS";
    static final String KEEP_ALIVE_MS_ENV_VAR_NAME_OLD = "FLAGD_KEEP_ALIVE_TIME";
    static final String KEEP_ALIVE_MS_ENV_VAR_NAME = "FLAGD_KEEP_ALIVE_TIME_MS";
    static final String TARGET_URI_ENV_VAR_NAME = "FLAGD_TARGET_URI";
    static final String STREAM_RETRY_GRACE_PERIOD = "FLAGD_RETRY_GRACE_PERIOD";
    static final String REINITIALIZE_ON_ERROR_ENV_VAR_NAME = "FLAGD_REINITIALIZE_ON_ERROR";

    static final String RESOLVER_RPC = "rpc";
    static final String RESOLVER_IN_PROCESS = "in-process";
    static final String RESOLVER_FILE = "file";

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

    static long fallBackToEnvOrDefault(String key, long defaultValue) {
        try {
            return System.getenv(key) != null ? Long.parseLong(System.getenv(key)) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    static List<String> fallBackToEnvOrDefaultList(String key, List<String> defaultValue) {
        try {
            return System.getenv(key) != null
                    ? Arrays.stream(System.getenv(key).split(","))
                            .map(String::trim)
                            .collect(Collectors.toList())
                    : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    static Resolver fromValueProvider(Function<String, String> provider) {
        final String resolverVar = provider.apply(RESOLVER_ENV_VAR);
        if (resolverVar == null) {
            return DEFAULT_RESOLVER_TYPE;
        }

        switch (resolverVar.toLowerCase()) {
            case "in-process":
                return Resolver.IN_PROCESS;
            case "rpc":
                return Resolver.RPC;
            case "file":
                return Resolver.FILE;
            default:
                log.warn("Unsupported resolver variable: {}", resolverVar);
                return DEFAULT_RESOLVER_TYPE;
        }
    }

    /** intermediate interface to unify deprecated Evaluator and new Resolver. */
    public interface EvaluatorType {
        String asString();
    }

    /**
     * flagd evaluator type. Deprecated : Please use {@code Config.Resolver}, which is a drop-in
     * replacement of this.
     */
    @Deprecated
    public enum Evaluator implements EvaluatorType {
        /**
         * This is the default resolver type, which connects to flagd instance with flag evaluation gRPC
         * contract. Evaluations are performed remotely.
         */
        RPC {
            public String asString() {
                return RESOLVER_RPC;
            }
        },
        /**
         * This is the in-process resolving type, where flags are fetched with flag sync gRPC contract
         * and stored locally for in-process evaluation. Evaluations are preformed in-process.
         */
        IN_PROCESS {
            public String asString() {
                return RESOLVER_IN_PROCESS;
            }
        }
    }

    /** flagd Resolver type. */
    public enum Resolver implements EvaluatorType {
        /**
         * This is the default resolver type, which connects to flagd instance with flag evaluation gRPC
         * contract. Evaluations are performed remotely.
         */
        RPC {
            public String asString() {
                return RESOLVER_RPC;
            }
        },
        /**
         * This is the in-process resolving type, where flags are fetched with flag sync gRPC contract
         * and stored locally for in-process evaluation. Evaluations are preformed in-process.
         */
        IN_PROCESS {
            public String asString() {
                return RESOLVER_IN_PROCESS;
            }
        },
        FILE {
            public String asString() {
                return RESOLVER_FILE;
            }
        }
    }
}
