package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.nameresolvers.EnvoyResolverProvider;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
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
@SuppressFBWarnings(value = "SE_BAD_FIELD", justification = "we don't care to serialize this")
public class ChannelBuilder {

    private static final Metadata.Key<String> FLAGD_SELECTOR_KEY =
            Metadata.Key.of("flagd-selector", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * Controls retry (not-reconnection) policy for failed RPCs.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Map<String, ?> buildRetryPolicy(final FlagdOptions options) {
        return new HashMap() {
            {
                put("methodConfig", Arrays.asList(new HashMap() {
                    {
                        put(
                                "name",
                                Arrays.asList(
                                        new HashMap() {
                                            {
                                                put("service", "flagd.sync.v1.FlagSyncService");
                                            }
                                        },
                                        new HashMap() {
                                            {
                                                put("service", "flagd.evaluation.v1.Service");
                                            }
                                        }));
                        put("retryPolicy", new HashMap() {
                            {
                                // 1 + 2 + 4
                                put("maxAttempts", 3.0); // types used here are important, need to be doubles
                                put("initialBackoff", "1s");
                                put(
                                        "maxBackoff",
                                        options.getRetryBackoffMaxMs() >= 1000
                                                ? String.format("%ds", options.getRetryBackoffMaxMs() / 1000)
                                                : "1s");
                                put("backoffMultiplier", 2.0);
                                // status codes to retry on:
                                put(
                                        "retryableStatusCodes",
                                        Arrays.asList(
                                                /*
                                                 * As per gRPC spec, the following status codes are safe to retry:
                                                 * UNAVAILABLE, UNKNOWN,
                                                 */
                                                Code.UNKNOWN.toString(), Code.UNAVAILABLE.toString()));
                            }
                        });
                    }
                }));
            }
        };
    }

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
            var channelBuilder = NettyChannelBuilder.forAddress(new DomainSocketAddress(options.getSocketPath()))
                    .keepAliveTime(keepAliveMs, TimeUnit.MILLISECONDS)
                    .eventLoopGroup(new MultiThreadIoEventLoopGroup(EpollIoHandler.newFactory()))
                    .channelType(EpollDomainSocketChannel.class)
                    .usePlaintext()
                    .defaultServiceConfig(buildRetryPolicy(options))
                    .enableRetry();

            // add header-based selector interceptor if selector is provided
            if (options.getSelector() != null) {
                channelBuilder.intercept(createSelectorInterceptor(options.getSelector()));
            }
            return channelBuilder.build();
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

            final NettyChannelBuilder channelBuilder =
                    NettyChannelBuilder.forTarget(targetUri).keepAliveTime(keepAliveMs, TimeUnit.MILLISECONDS);

            if (options.getDefaultAuthority() != null) {
                channelBuilder.overrideAuthority(options.getDefaultAuthority());
            }
            if (options.getClientInterceptors() != null) {
                channelBuilder.intercept(options.getClientInterceptors());
            }
            if (options.isTls()) {
                SslContextBuilder sslContext = GrpcSslContexts.forClient();

                if (options.getCertPath() != null) {
                    final File file = new File(options.getCertPath());
                    if (file.exists()) {
                        sslContext.trustManager(file);
                    }
                }

                channelBuilder.sslContext(sslContext.build());
            } else {
                channelBuilder.usePlaintext();
            }

            // telemetry interceptor if option is provided
            if (options.getOpenTelemetry() != null) {
                channelBuilder.intercept(new FlagdGrpcInterceptor(options.getOpenTelemetry()));
            }
            // add header-based selector interceptor if selector is provided
            if (options.getSelector() != null) {
                channelBuilder.intercept(createSelectorInterceptor(options.getSelector()));
            }

            return channelBuilder
                    .defaultServiceConfig(buildRetryPolicy(options))
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

    /**
     * Creates a ClientInterceptor that adds the flagd-selector header to gRPC requests.
     * This is the preferred approach for passing selectors as per flagd issue #1814.
     *
     * @param selector the selector value to pass in the header
     * @return a ClientInterceptor that adds the flagd-selector header
     */
    private static ClientInterceptor createSelectorInterceptor(String selector) {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
                return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                        next.newCall(method, callOptions)) {
                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        headers.put(FLAGD_SELECTOR_KEY, selector);
                        super.start(responseListener, headers);
                    }
                };
            }
        };
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
