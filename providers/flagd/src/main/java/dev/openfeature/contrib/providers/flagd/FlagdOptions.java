package dev.openfeature.contrib.providers.flagd;

import io.opentelemetry.api.GlobalOpenTelemetry;
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
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_TLS;
import static dev.openfeature.contrib.providers.flagd.Config.HOST_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.MAX_CACHE_SIZE_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.MAX_EVENT_STREAM_RETRIES_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.OFFLINE_SOURCE_PATH;
import static dev.openfeature.contrib.providers.flagd.Config.PORT_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.SERVER_CERT_PATH_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.SOCKET_PATH_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.SOURCE_SELECTOR_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.TLS_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.fallBackToEnvOrDefault;
import static dev.openfeature.contrib.providers.flagd.Config.fromValueProvider;

/**
 * FlagdOptions is a builder to build flagd provider options.
 */
@Builder
@Getter
@SuppressWarnings("PMD.TooManyStaticImports")
public class FlagdOptions {

    /**
     * flagd resolving type.
     */
    private Config.EvaluatorType resolverType;

    /**
     * flagd connection host.
     */
    @Builder.Default
    private String host = fallBackToEnvOrDefault(HOST_ENV_VAR_NAME, DEFAULT_HOST);

    /**
     * flagd connection port.
     */
    private int port;

    /**
     * Use TLS connectivity.
     */
    @Builder.Default
    private boolean tls = Boolean.parseBoolean(fallBackToEnvOrDefault(TLS_ENV_VAR_NAME, DEFAULT_TLS));

    /**
     * TLS certificate overriding if TLS connectivity is used.
     */
    @Builder.Default
    private String certPath = fallBackToEnvOrDefault(SERVER_CERT_PATH_ENV_VAR_NAME, null);

    /**
     * Unix socket path to flagd.
     */
    @Builder.Default
    private String socketPath = fallBackToEnvOrDefault(SOCKET_PATH_ENV_VAR_NAME, null);

    /**
     * Cache type to use. Supports - lru, disabled.
     */
    @Builder.Default
    private String cacheType = fallBackToEnvOrDefault(CACHE_ENV_VAR_NAME, DEFAULT_CACHE);

    /**
     * Max cache size.
     */
    @Builder.Default
    private int maxCacheSize = fallBackToEnvOrDefault(MAX_CACHE_SIZE_ENV_VAR_NAME, DEFAULT_MAX_CACHE_SIZE);

    /**
     * Max event stream connection retries.
     */
    @Builder.Default
    private int maxEventStreamRetries =
            fallBackToEnvOrDefault(MAX_EVENT_STREAM_RETRIES_ENV_VAR_NAME, DEFAULT_MAX_EVENT_STREAM_RETRIES);

    /**
     * Backoff interval in milliseconds.
     */
    @Builder.Default
    private int retryBackoffMs =
            fallBackToEnvOrDefault(BASE_EVENT_STREAM_RETRY_BACKOFF_MS_ENV_VAR_NAME, BASE_EVENT_STREAM_RETRY_BACKOFF_MS);


    /**
     * Connection deadline in milliseconds.
     * For RPC resolving, this is the deadline to connect to flagd for flag evaluation.
     * For in-process resolving, this is the deadline for sync stream termination.
     */
    @Builder.Default
    private int deadline = fallBackToEnvOrDefault(DEADLINE_MS_ENV_VAR_NAME, DEFAULT_DEADLINE);

    /**
     * Selector to be used with flag sync gRPC contract.
     **/
    @Builder.Default
    private String selector = fallBackToEnvOrDefault(SOURCE_SELECTOR_ENV_VAR_NAME, null);

    /**
     * File source of flags to be used by offline mode.
     * Setting this enables the offline mode of the in-process provider.
     */
    @Builder.Default
    private String offlineFlagSourcePath = fallBackToEnvOrDefault(OFFLINE_SOURCE_PATH, null);

    /**
     * Inject OpenTelemetry for the library runtime. Providing sdk will initiate distributed tracing for flagd grpc
     * connectivity.
     */
    private OpenTelemetry openTelemetry;


    /**
     * Builder overwrite in order to customize the "build" method.
     *
     * @return the flagd options builder
     */
    public static FlagdOptionsBuilder builder() {
        return new FlagdOptionsBuilder() {
            @Override
            public FlagdOptions build() {
                prebuild();
                return super.build();
            }
        };
    }

    /**
     * Overload default lombok builder.
     */
    public static class FlagdOptionsBuilder {
        /**
         * Enable OpenTelemetry instance extraction from GlobalOpenTelemetry. Note that, this is only useful if global
         * configurations are registered.
         */
        public FlagdOptionsBuilder withGlobalTelemetry(final boolean b) {
            if (b) {
                this.openTelemetry = GlobalOpenTelemetry.get();
            }
            return this;
        }

        void prebuild() {
            if (resolverType == null) {
                resolverType = fromValueProvider(System::getenv);
            }

            if (port == 0) {
                port = Integer.parseInt(fallBackToEnvOrDefault(PORT_ENV_VAR_NAME, determineDefaultPortForResolver()));
            }
        }

        private String determineDefaultPortForResolver() {
            if (resolverType.equals(Config.Resolver.RPC)) {
                return Config.DEFAULT_RPC_PORT;
            }
            return Config.DEFAULT_IN_PROCESS_PORT;
        }
    }
}
