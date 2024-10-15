package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.nameresolvers.EnvoyResolverProvider;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.ManagedChannel;
import io.grpc.NameResolverRegistry;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.SSLException;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * gRPC channel builder helper.
 */
public class ChannelBuilder {

    private ChannelBuilder() {
    }

    /**
     * This method is a helper to build a {@link ManagedChannel} from provided {@link FlagdOptions}.
     */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "certificate path is a user input")
    public static ManagedChannel nettyChannel(final FlagdOptions options) {

        // keepAliveTime: Long.MAX_VALUE disables keepAlive; very small values are increased automatically
        long keepAliveMs = options.getKeepAlive() == 0 ? Long.MAX_VALUE : options.getKeepAlive();

        // we have a socket path specified, build a channel with a unix socket
        if (options.getSocketPath() != null) {
            // check epoll availability
            if (!Epoll.isAvailable()) {
                throw new IllegalStateException("unix socket cannot be used", Epoll.unavailabilityCause());
            }

            return NettyChannelBuilder
                    .forAddress(new DomainSocketAddress(options.getSocketPath()))
                    .keepAliveTime(keepAliveMs, TimeUnit.MILLISECONDS)
                    .eventLoopGroup(new EpollEventLoopGroup())
                    .channelType(EpollDomainSocketChannel.class)
                    .usePlaintext()
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
            final String targetUri = isValidTargetUri(options.getTargetUri()) ? options.getTargetUri() :
                    defaultTarget;

            final NettyChannelBuilder builder = NettyChannelBuilder
                    .forTarget(targetUri)
                    .keepAliveTime(keepAliveMs, TimeUnit.MILLISECONDS);

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

            return builder.build();
        } catch (SSLException ssle) {
            SslConfigException sslConfigException = new SslConfigException("Error with SSL configuration.");
            sslConfigException.initCause(ssle);
            throw sslConfigException;
        } catch (IllegalArgumentException argumentException) {
            GenericConfigException genericConfigException = new GenericConfigException(
                    "Error with gRPC target string configuration");
            genericConfigException.initCause(argumentException);
            throw genericConfigException;
        }
    }

    private static  boolean isValidTargetUri(String targetUri) {
        if (targetUri == null) {
            return false;
        }

        try {
            final String scheme = new URI(targetUri).getScheme();
            if (scheme.equals(SupportedScheme.ENVOY.getScheme()) || scheme.equals(SupportedScheme.DNS.getScheme())
                    || scheme.equals(SupportedScheme.XDS.getScheme())
                    q|| scheme.equals(SupportedScheme.UDS.getScheme())) {
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
