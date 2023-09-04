package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.SSLException;
import java.io.File;

/**
 * gRPC channel builder helper.
 */
public class ChannelBuilder {
    /**
     * This method is a helper to build a {@link ManagedChannel} from provided {@link FlagdOptions}.
     */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "certificate path is a user input")
    public static ManagedChannel nettyChannel(final FlagdOptions options) {
        // we have a socket path specified, build a channel with a unix socket
        if (options.getSocketPath() != null) {
            // check epoll availability
            if (!Epoll.isAvailable()) {
                throw new IllegalStateException("unix socket cannot be used", Epoll.unavailabilityCause());
            }

            return NettyChannelBuilder
                    .forAddress(new DomainSocketAddress(options.getSocketPath()))
                    .eventLoopGroup(new EpollEventLoopGroup())
                    .channelType(EpollDomainSocketChannel.class)
                    .usePlaintext()
                    .build();
        }

        // build a TCP socket
        try {
            final NettyChannelBuilder builder = NettyChannelBuilder.forAddress(options.getHost(), options.getPort());
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
        }
    }
}
