package dev.openfeature.contrib.providers.flagd;

import static dev.openfeature.contrib.providers.flagd.Config.fallBackToEnvOrDefault;
import static dev.openfeature.contrib.providers.flagd.Config.fallBackToEnvOrDefaultList;
import static dev.openfeature.contrib.providers.flagd.Config.fromValueProvider;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Structure;
import io.grpc.ClientInterceptor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import java.util.List;
import java.util.function.Function;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * FlagdOptions is a builder to build flagd provider options.
 */
@Builder(toBuilder = true)
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
    private String host = fallBackToEnvOrDefault(Config.HOST_ENV_VAR_NAME, Config.DEFAULT_HOST);

    /**
     * flagd connection port.
     */
    private int port;

    // TODO: remove the metadata call entirely after https://github.com/open-feature/flagd/issues/1584
    /**
     * Disables call to sync.GetMetadata (see:
     * https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1#flagd.sync.v1.FlagSyncService.GetMetadata).
     * Disabling will prevent static context from flagd being used in evaluations.
     * GetMetadata and this option will be removed.
     */
    @Deprecated
    @Builder.Default
    private boolean syncMetadataDisabled = false;

    /**
     * Use TLS connectivity.
     */
    @Builder.Default
    private boolean tls = Boolean.parseBoolean(fallBackToEnvOrDefault(Config.TLS_ENV_VAR_NAME, Config.DEFAULT_TLS));

    /**
     * TLS certificate overriding if TLS connectivity is used.
     */
    @Builder.Default
    private String certPath = fallBackToEnvOrDefault(Config.SERVER_CERT_PATH_ENV_VAR_NAME, null);

    /**
     * Unix socket path to flagd.
     */
    @Builder.Default
    private String socketPath = fallBackToEnvOrDefault(Config.SOCKET_PATH_ENV_VAR_NAME, null);

    /**
     * Cache type to use. Supports - lru, disabled.
     */
    @Builder.Default
    private String cacheType = fallBackToEnvOrDefault(Config.CACHE_ENV_VAR_NAME, Config.DEFAULT_CACHE);

    /**
     * Max cache size.
     */
    @Builder.Default
    private int maxCacheSize =
            fallBackToEnvOrDefault(Config.MAX_CACHE_SIZE_ENV_VAR_NAME, Config.DEFAULT_MAX_CACHE_SIZE);

    /**
     * Backoff interval in milliseconds.
     */
    @Builder.Default
    private int retryBackoffMs = fallBackToEnvOrDefault(
            Config.BASE_EVENT_STREAM_RETRY_BACKOFF_MS_ENV_VAR_NAME, Config.BASE_EVENT_STREAM_RETRY_BACKOFF_MS);

    /**
     * Connection deadline in milliseconds.
     * For RPC resolving, this is the deadline to connect to flagd for flag
     * evaluation.
     * For in-process resolving, this is the deadline for sync stream termination.
     */
    @Builder.Default
    private int deadline = fallBackToEnvOrDefault(Config.DEADLINE_MS_ENV_VAR_NAME, Config.DEFAULT_DEADLINE);

    /**
     * Max stream retry backoff in milliseconds.
     */
    @Builder.Default
    private int retryBackoffMaxMs =
            fallBackToEnvOrDefault(Config.FLAGD_RETRY_BACKOFF_MAX_MS_VAR_NAME, Config.DEFAULT_MAX_RETRY_BACKOFF_MS);

    /**
     * Streaming connection deadline in milliseconds.
     * Set to 0 to disable the deadline.
     * Defaults to 600000 (10 minutes); recommended to prevent infrastructure from
     * killing idle connections.
     */
    @Builder.Default
    private int streamDeadlineMs =
            fallBackToEnvOrDefault(Config.STREAM_DEADLINE_MS_ENV_VAR_NAME, Config.DEFAULT_STREAM_DEADLINE_MS);

    /**
     * Grace time period in seconds before provider moves from STALE to ERROR.
     * Defaults to 5
     */
    @Builder.Default
    private int retryGracePeriod =
            fallBackToEnvOrDefault(Config.STREAM_RETRY_GRACE_PERIOD, Config.DEFAULT_STREAM_RETRY_GRACE_PERIOD);

    /**
     * List of grpc response status codes for which the provider transitions into fatal state upon first connection.
     * Defaults to empty list
     */
    @Builder.Default
    private List<String> fatalStatusCodes =
            fallBackToEnvOrDefaultList(Config.FATAL_STATUS_CODES_ENV_VAR_NAME, List.of());

    /**
     * Selector to be used with flag sync gRPC contract.
     *
     * <p>The SDK automatically passes the selector via the {@code flagd-selector} gRPC metadata header
     * (the preferred approach per <a href="https://github.com/open-feature/flagd/issues/1814">flagd issue #1814</a>).
     * For backward compatibility with older flagd versions, the selector is also sent in the request body.
     *
     * <p>Only applicable for in-process resolver mode.
     *
     * @see <a href="https://github.com/open-feature/java-sdk-contrib/tree/main/providers/flagd#selector-filtering-in-process-mode-only">Selector filtering documentation</a>
     **/
    @Builder.Default
    private String selector = fallBackToEnvOrDefault(Config.SOURCE_SELECTOR_ENV_VAR_NAME, null);

    /**
     * ProviderId to be used with flag sync gRPC contract.
     **/
    @Builder.Default
    private String providerId = fallBackToEnvOrDefault(
            Config.PROVIDER_ID_ENV_VAR_NAME, fallBackToEnvOrDefault(Config.SOURCE_PROVIDER_ID_ENV_VAR_NAME, null));

    /**
     * gRPC client KeepAlive in milliseconds. Disabled with 0.
     * Defaults to 0 (disabled).
     **/
    @Builder.Default
    private long keepAlive = fallBackToEnvOrDefault(
            Config.KEEP_ALIVE_MS_ENV_VAR_NAME,
            fallBackToEnvOrDefault(Config.KEEP_ALIVE_MS_ENV_VAR_NAME_OLD, Config.DEFAULT_KEEP_ALIVE));

    /**
     * File source of flags to be used by offline mode.
     * Setting this enables the offline mode of the in-process provider.
     */
    private String offlineFlagSourcePath;

    /**
     * File polling interval.
     * Defaults to 0 (disabled).
     **/
    @Builder.Default
    private int offlinePollIntervalMs = fallBackToEnvOrDefault(Config.OFFLINE_POLL_MS, Config.DEFAULT_OFFLINE_POLL_MS);

    /**
     * gRPC custom target string.
     *
     * <p>Setting this will allow user to use custom gRPC name resolver at present
     * we are supporting all core resolver along with a custom resolver for envoy proxy
     * resolution. For more visit (https://grpc.io/docs/guides/custom-name-resolution/)
     */
    @Builder.Default
    private String targetUri = fallBackToEnvOrDefault(Config.TARGET_URI_ENV_VAR_NAME, null);

    /**
     * Function providing an EvaluationContext to mix into every evaluations.
     * The sync-metadata response
     * (https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1#flagd.sync.v1.GetMetadataResponse),
     * represented as a {@link dev.openfeature.sdk.Structure}, is passed as an
     * argument.
     * This function runs every time the provider (re)connects, and its result is cached and used in every evaluation.
     * By default, the entire sync response (converted to a Structure) is used.
     */
    @Builder.Default
    private Function<Structure, EvaluationContext> contextEnricher =
            (syncMetadata) -> new ImmutableContext(syncMetadata.asMap());

    /**
     * Inject a Custom Connector for fetching flags.
     */
    private QueueSource customConnector;

    /**
     * Inject OpenTelemetry for the library runtime. Providing sdk will initiate
     * distributed tracing for flagd grpc
     * connectivity.
     */
    private OpenTelemetry openTelemetry;

    /**
     * gRPC client interceptors to be used when creating a gRPC channel.
     */
    @Builder.Default
    private List<ClientInterceptor> clientInterceptors = null;

    /**
     * Authority header to be used when creating a gRPC channel.
     */
    @Builder.Default
    private String defaultAuthority = fallBackToEnvOrDefault(Config.DEFAULT_AUTHORITY_ENV_VAR_NAME, null);

    /**
     * !EXPERIMENTAL!
     * Whether to reinitialize the channel (TCP connection) after the grace period is exceeded.
     * This can help recover from connection issues by creating fresh connections.
     * Particularly useful for troubleshooting network issues related to proxies or service meshes.
     */
    @Builder.Default
    private boolean reinitializeOnError = Boolean.parseBoolean(
            fallBackToEnvOrDefault(Config.REINITIALIZE_ON_ERROR_ENV_VAR_NAME, Config.DEFAULT_REINITIALIZE_ON_ERROR));

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
         * Enable OpenTelemetry instance extraction from GlobalOpenTelemetry. Note that,
         * this is only useful if global
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

            if (StringUtils.isBlank(offlineFlagSourcePath)) {
                offlineFlagSourcePath = fallBackToEnvOrDefault(Config.OFFLINE_SOURCE_PATH, null);
            }

            if (!StringUtils.isEmpty(offlineFlagSourcePath) && resolverType == Config.Resolver.IN_PROCESS) {
                resolverType = Config.Resolver.FILE;
            }

            // We need a file path for FILE Provider
            if (StringUtils.isEmpty(offlineFlagSourcePath) && resolverType == Config.Resolver.FILE) {
                throw new IllegalArgumentException("Resolver Type 'FILE' requires a offlineFlagSourcePath");
            }

            if (port == 0 && resolverType != Config.Resolver.FILE) {
                String defaultPort = determineDefaultPortForResolver();
                String fromPortEnv = fallBackToEnvOrDefault(Config.PORT_ENV_VAR_NAME, defaultPort);

                String portValue = resolverType == Config.Resolver.IN_PROCESS
                        ? fallBackToEnvOrDefault(Config.SYNC_PORT_ENV_VAR_NAME, fromPortEnv)
                        : fromPortEnv;

                port = Integer.parseInt(portValue);
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
