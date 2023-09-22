package dev.openfeature.contrib.providers.flagd;

import io.opentelemetry.api.OpenTelemetry;
import lombok.Builder;
import lombok.Getter;

import static dev.openfeature.contrib.providers.flagd.Config.BASE_EVENT_STREAM_RETRY_BACKOFF_MS;
import static dev.openfeature.contrib.providers.flagd.Config.BASE_EVENT_STREAM_RETRY_BACKOFF_MS_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.CACHE_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.DEADLINE_MS_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_CACHE;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_DEADLINE;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_HOST;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_MAX_CACHE_SIZE;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_MAX_EVENT_STREAM_RETRIES;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_PORT;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_RESOLVER_TYPE;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_TLS;
import static dev.openfeature.contrib.providers.flagd.Config.HOST_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.MAX_CACHE_SIZE_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.MAX_EVENT_STREAM_RETRIES_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.PORT_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.SOURCE_SELECTOR_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.SERVER_CERT_PATH_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.SOCKET_PATH_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.TLS_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.fallBackToEnvOrDefault;

/**
 * FlagdOptions is a builder to build flagd provider options.
 * */
@Builder
@Getter
@SuppressWarnings("PMD.TooManyStaticImports")
public class FlagdOptions {

    /**
     * flagd resolving type.
     * */
    @Builder.Default
    private Config.Evaluator resolverType = DEFAULT_RESOLVER_TYPE;

    /**
     * flagd connection host.
     * */
    @Builder.Default
    private String host = fallBackToEnvOrDefault(HOST_ENV_VAR_NAME, DEFAULT_HOST);

    /**
     * flagd connection port.
     * */
    @Builder.Default
    private int port = Integer.parseInt(fallBackToEnvOrDefault(PORT_ENV_VAR_NAME, DEFAULT_PORT));

    /**
     * Use TLS connectivity.
     * */
    @Builder.Default
    private boolean tls = Boolean.parseBoolean(fallBackToEnvOrDefault(TLS_ENV_VAR_NAME, DEFAULT_TLS));

    /**
     * TLS certificate overriding if TLS connectivity is used.
     * */
    @Builder.Default
    private String certPath = fallBackToEnvOrDefault(SERVER_CERT_PATH_ENV_VAR_NAME, null);

    /**
     * Unix socket path to flagd.
     * */
    @Builder.Default
    private String socketPath = fallBackToEnvOrDefault(SOCKET_PATH_ENV_VAR_NAME, null);

    /**
     * Cache type to use. Supports - lru, disabled.
     * */
    @Builder.Default
    private String cacheType = fallBackToEnvOrDefault(CACHE_ENV_VAR_NAME, DEFAULT_CACHE);

    /**
     * Max cache size.
     * */
    @Builder.Default
    private int maxCacheSize = fallBackToEnvOrDefault(MAX_CACHE_SIZE_ENV_VAR_NAME, DEFAULT_MAX_CACHE_SIZE);

    /**
     * Max event stream connection retries.
     * */
    @Builder.Default
    private int maxEventStreamRetries =
            fallBackToEnvOrDefault(MAX_EVENT_STREAM_RETRIES_ENV_VAR_NAME, DEFAULT_MAX_EVENT_STREAM_RETRIES);

    /**
     * Backoff interval in milliseconds.
     * */
    @Builder.Default
    private int retryBackoffMs =
            fallBackToEnvOrDefault(BASE_EVENT_STREAM_RETRY_BACKOFF_MS_ENV_VAR_NAME, BASE_EVENT_STREAM_RETRY_BACKOFF_MS);


    /**
     * Connection deadline in milliseconds.
     * For RPC resolving, this is the deadline to connect to flagd for flag evaluation.
     * For in-process resolving, this is the deadline for sync stream termination.
     * */
    @Builder.Default
    private int deadline = fallBackToEnvOrDefault(DEADLINE_MS_ENV_VAR_NAME, DEFAULT_DEADLINE);

    /**
     * Selector to be used with flag sync gRPC contract.
     **/
    @Builder.Default
    private String selector = fallBackToEnvOrDefault(SOURCE_SELECTOR_ENV_VAR_NAME, null);

    /**
     * Inject OpenTelemetry for the library runtime. Providing sdk will initiate distributed tracing for flagd grpc
     * connectivity.
     * */
    private OpenTelemetry openTelemetry;
}
