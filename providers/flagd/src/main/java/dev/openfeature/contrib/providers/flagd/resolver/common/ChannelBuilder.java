package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.nameresolvers.EnvoyResolverProvider;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.ManagedChannel;
import io.grpc.NameResolverRegistry;
import io.grpc.Status.Code;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;

/** gRPC channel builder helper. */
public class ChannelBuilder {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final Map<String, ?> DEFAULT_RETRY_POLICY = new HashMap() {
        {
            // 1 + 2 + 4
            put("maxAttempts", 3.0); // types used here are important, need to be doubles
            put("initialBackoff", "1s");
            put("maxBackoff", "5s");
            put("backoffMultiplier", 2.0);
            // status codes to retry on:
            put("retryableStatusCodes",
                Arrays.asList(
                        /*
                         * Only states UNAVAILABLE and UNKNOWN should be retried. All
                         * other failure states will probably not be resolved with a simple retry.
                         */
                        Code.UNAVAILABLE.toString(),
                        Code.UNKNOWN.toString()
                )
            );
        }
    };

    /**
     * Controls retry (not-reconnection) policy for failed RPCs.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static final Map<String, ?> SERVICE_CONFIG_WITH_RETRY = new HashMap() {
        {
            put("methodConfig", Arrays.asList(
                    new HashMap() {
                        {
                            put("name", Arrays.asList(
                                    new HashMap() {
                                        {
                                            put("service", "flagd.sync.v1.FlagSyncService");
                                        }
                                    },
                                    new HashMap() {
                                        {
                                            put("service", "flagd.evaluation.v1.Service");
                                        }
                                    }
                                ));
                            put("retryPolicy", DEFAULT_RETRY_POLICY);
                        }

                        {
                            put("name", Arrays.asList(
                                    new HashMap() {
                                        {
                                            put("service", "flagd.sync.v1.FlagSyncService");
                                            put("method", "SyncFlags");
                                        }
                                    }
                                ));
                            put("retryPolicy", new HashMap(DEFAULT_RETRY_POLICY) {
                                {
                                    // 1 + 2 + 4 + 5 + 5 + 5 + 5 + 5 + 5 + 5
                                    put("maxAttempts", 12.0);
                                    // for streaming Retry on more status codes
                                    put("retryableStatusCodes",
                                            Arrays.asList(
                                                    /*
                                                     * All codes are retryable except OK and DEADLINE_EXCEEDED since
                                                     * any others not listed here cause a very tight loop of retries.
                                                     * DEADLINE_EXCEEDED is typically a result of a client specified
                                                     * deadline,and definitionally should not result in a tight loop
                                                     * (it's a timeout).
                                                     */
                                                    Code.CANCELLED.toString(),
                                                    Code.UNKNOWN.toString(),
                                                    Code.INVALID_ARGUMENT.toString(),
                                                    Code.NOT_FOUND.toString(),
                                                    Code.ALREADY_EXISTS.toString(),
                                                    Code.PERMISSION_DENIED.toString(),
                                                    Code.RESOURCE_EXHAUSTED.toString(),
                                                    Code.FAILED_PRECONDITION.toString(),
                                                    Code.ABORTED.toString(),
                                                    Code.OUT_OF_RANGE.toString(),
                                                    Code.UNIMPLEMENTED.toString(),
                                                    Code.INTERNAL.toString(),
                                                    Code.UNAVAILABLE.toString(),
                                                    Code.DATA_LOSS.toString(),
                                                    Code.UNAUTHENTICATED.toString()
                                            )
                                    );
                                }
                            });
                        }
                    }
            ));
        }
    };

    private ChannelBuilder() {}

    /**
     * This method is a helper to build a {@link ManagedChannel} from provided {@link FlagdOptions}.
     */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "certificate path is a user input")
    public static ManagedChannel nettyChannel(final FlagdOptions options) {

        // keepAliveTime: Long.MAX_VALUE disables keepAlive; very small values are increased
        // automatically
        long keepAliveMs = options.getKeepAlive() == 0 ? Long.MAX_VALUE : options.getKeepAlive();

        // we have a socket path specified, build a channel with a unix socket
        if (options.getSocketPath() != null) {
            // check epoll availability
            if (!Epoll.isAvailable()) {
                throw new IllegalStateException("unix socket cannot be used", Epoll.unavailabilityCause());
            }
            return NettyChannelBuilder.forAddress(new DomainSocketAddress(options.getSocketPath()))
                    .keepAliveTime(keepAliveMs, TimeUnit.MILLISECONDS)
                    .eventLoopGroup(new MultiThreadIoEventLoopGroup(EpollIoHandler.newFactory()))
                    .channelType(EpollDomainSocketChannel.class)
                    .usePlaintext()
                    .defaultServiceConfig(SERVICE_CONFIG_WITH_RETRY)
                    .enableRetry()
                    .build();
        }

        // build a TCP socket
        try {
            // Register custom resolver
            if (isEnvoyTarget(options.getTargetUri())) {
                NameResolverRegistry.getDefaultRegistry().register(new EnvoyResolverProvider());
            }

            // default to current `dns` resolution i.e. <host>:<port>, if valid / supported
            // target string use the user provided target uri.
            final String defaultTarget = String.format("%s:%s", options.getHost(), options.getPort());
            final String targetUri = isValidTargetUri(options.getTargetUri()) ? options.getTargetUri() : defaultTarget;

            final NettyChannelBuilder builder =
                    NettyChannelBuilder.forTarget(targetUri).keepAliveTime(keepAliveMs, TimeUnit.MILLISECONDS);

            if (options.getDefaultAuthority() != null) {
                builder.overrideAuthority(options.getDefaultAuthority());
            }
            if (options.getClientInterceptors() != null) {
                builder.intercept(options.getClientInterceptors());
            }
            if (options.isTls()) {
                SslContextBuilder sslContext = GrpcSslContexts.forClient();

                if (options.getCertPath() != null) {
                    final File file = new File(options.getCertPath());
                    if (file.exists()) {
                        sslContext.trustManager(file);
                    }
                }

                builder.sslContext(sslContext.build());
            } else {
                builder.usePlaintext();
            }

            // telemetry interceptor if option is provided
            if (options.getOpenTelemetry() != null) {
                builder.intercept(new FlagdGrpcInterceptor(options.getOpenTelemetry()));
            }

            return builder.defaultServiceConfig(SERVICE_CONFIG_WITH_RETRY)
                    .enableRetry()
                    .build();
        } catch (SSLException ssle) {
            SslConfigException sslConfigException = new SslConfigException("Error with SSL configuration.");
            sslConfigException.initCause(ssle);
            throw sslConfigException;
        } catch (IllegalArgumentException argumentException) {
            GenericConfigException genericConfigException =
                    new GenericConfigException("Error with gRPC target string configuration");
            genericConfigException.initCause(argumentException);
            throw genericConfigException;
        }
    }

    private static boolean isValidTargetUri(String targetUri) {
        if (targetUri == null) {
            return false;
        }

        try {
            final String scheme = new URI(targetUri).getScheme();
            if (scheme.equals(SupportedScheme.ENVOY.getScheme())
                    || scheme.equals(SupportedScheme.DNS.getScheme())
                    || scheme.equals(SupportedScheme.XDS.getScheme())
                    || scheme.equals(SupportedScheme.UDS.getScheme())) {
                return true;
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid target string", e);
        }

        return false;
    }

    private static boolean isEnvoyTarget(String targetUri) {
        if (targetUri == null) {
            return false;
        }

        try {
            final String scheme = new URI(targetUri).getScheme();
            if (scheme.equals(SupportedScheme.ENVOY.getScheme())) {
                return true;
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid target string", e);
        }

        return false;
    }
}
