package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.ssl.SslContextBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import javax.net.ssl.SSLKeyException;
import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChannelBuilderTest {

    @Test
    @EnabledOnOs(OS.LINUX)
    void testNettyChannel_withSocketPath() {
        try (MockedStatic<Epoll> epollMock = mockStatic(Epoll.class);
             MockedStatic<NettyChannelBuilder> nettyMock = mockStatic(NettyChannelBuilder.class)) {

            // Mocks
            epollMock.when(Epoll::isAvailable).thenReturn(true);
            NettyChannelBuilder mockBuilder = mock(NettyChannelBuilder.class);
            ManagedChannel mockChannel = mock(ManagedChannel.class);

            nettyMock.when(() -> NettyChannelBuilder.forAddress(any(DomainSocketAddress.class)))
                    .thenReturn(mockBuilder);

            when(mockBuilder.keepAliveTime(anyLong(), any(TimeUnit.class))).thenReturn(mockBuilder);
            when(mockBuilder.eventLoopGroup(any(EpollEventLoopGroup.class))).thenReturn(mockBuilder);
            when(mockBuilder.channelType(EpollDomainSocketChannel.class)).thenReturn(mockBuilder);
            when(mockBuilder.usePlaintext()).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockChannel);

            // Input options
            FlagdOptions options = FlagdOptions.builder()
                    .socketPath("/path/to/socket")
                    .keepAlive(1000)
                    .build();

            // Call method under test
            ManagedChannel channel = ChannelBuilder.nettyChannel(options);

            // Assertions
            assertThat(channel).isEqualTo(mockChannel);
            nettyMock.verify(() -> NettyChannelBuilder.forAddress(new DomainSocketAddress("/path/to/socket")));
            verify(mockBuilder).keepAliveTime(1000, TimeUnit.MILLISECONDS);
            verify(mockBuilder).eventLoopGroup(any(EpollEventLoopGroup.class));
            verify(mockBuilder).channelType(EpollDomainSocketChannel.class);
            verify(mockBuilder).usePlaintext();
            verify(mockBuilder).build();
        }
    }

    @Test
    void testNettyChannel_withTlsAndCert() {
        try (MockedStatic<NettyChannelBuilder> nettyMock = mockStatic(NettyChannelBuilder.class)) {
            // Mocks
            NettyChannelBuilder mockBuilder = mock(NettyChannelBuilder.class);
            ManagedChannel mockChannel = mock(ManagedChannel.class);
            nettyMock.when(() -> NettyChannelBuilder.forTarget("localhost:8080"))
                    .thenReturn(mockBuilder);

            when(mockBuilder.keepAliveTime(anyLong(), any(TimeUnit.class))).thenReturn(mockBuilder);
            when(mockBuilder.sslContext(any())).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockChannel);

            File mockCert = mock(File.class);
            when(mockCert.exists()).thenReturn(true);
            String path = "test-harness/ssl/custom-root-cert.crt";

            File file = new File(path);
            String absolutePath = file.getAbsolutePath();
            // Input options
            FlagdOptions options = FlagdOptions.builder()
                    .host("localhost")
                    .port(8080)
                    .keepAlive(5000)
                    .tls(true)
                    .certPath(absolutePath)
                    .build();

            // Call method under test
            ManagedChannel channel = ChannelBuilder.nettyChannel(options);

            // Assertions
            assertThat(channel).isEqualTo(mockChannel);
            nettyMock.verify(() -> NettyChannelBuilder.forTarget("localhost:8080"));
            verify(mockBuilder).keepAliveTime(5000, TimeUnit.MILLISECONDS);
            verify(mockBuilder).sslContext(any());
            verify(mockBuilder).build();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/incorrect/{uri}/;)"})
    void testNettyChannel_withInvalidTargetUri(String uri) {
        FlagdOptions options = FlagdOptions.builder()
                .targetUri(uri)
                .build();

        assertThatThrownBy(() -> ChannelBuilder.nettyChannel(options))
                .isInstanceOf(GenericConfigException.class)
                .hasMessageContaining("Error with gRPC target string configuration");
    }

    @Test
    void testNettyChannel_epollNotAvailable() {
        try (MockedStatic<Epoll> epollMock = mockStatic(Epoll.class)) {
            epollMock.when(Epoll::isAvailable).thenReturn(false);

            FlagdOptions options = FlagdOptions.builder().socketPath("/path/to/socket").build();

            assertThatThrownBy(() -> ChannelBuilder.nettyChannel(options))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unix socket cannot be used");
        }
    }

    @Test
    void testNettyChannel_sslException() throws Exception {
        try (MockedStatic<NettyChannelBuilder> nettyMock = mockStatic(NettyChannelBuilder.class)) {
            NettyChannelBuilder mockBuilder = mock(NettyChannelBuilder.class);
            nettyMock.when(() -> NettyChannelBuilder.forTarget(anyString())).thenReturn(mockBuilder);
            try (MockedStatic<GrpcSslContexts> sslmock = mockStatic(GrpcSslContexts.class)) {
                SslContextBuilder sslMockBuilder = mock(SslContextBuilder.class);
                sslmock.when(GrpcSslContexts::forClient).thenReturn(sslMockBuilder);
                when(sslMockBuilder.build()).thenThrow(new SSLKeyException("Test SSL error"));
                when(mockBuilder.keepAliveTime(anyLong(), any(TimeUnit.class))).thenReturn(mockBuilder);

                FlagdOptions options = FlagdOptions.builder().tls(true).build();

                assertThatThrownBy(() -> ChannelBuilder.nettyChannel(options))
                        .isInstanceOf(SslConfigException.class)
                        .hasMessageContaining("Error with SSL configuration");
            }
        }
    }
}