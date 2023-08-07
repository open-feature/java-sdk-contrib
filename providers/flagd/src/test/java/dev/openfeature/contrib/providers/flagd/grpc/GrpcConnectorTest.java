package dev.openfeature.contrib.providers.flagd.grpc;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.flagd.grpc.ServiceGrpc;
import io.grpc.Channel;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

public class GrpcConnectorTest {

    @Test
    void path_arg_should_build_domain_socket_with_correct_path() {
        final String path = "/some/path";

        ServiceGrpc.ServiceBlockingStub mockBlockingStub = mock(ServiceGrpc.ServiceBlockingStub.class);
        ServiceGrpc.ServiceStub mockStub = mock(ServiceGrpc.ServiceStub.class);
        NettyChannelBuilder mockChannelBuilder = getMockChannelBuilderSocket();

        try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
            mockStaticService.when(() -> ServiceGrpc.newBlockingStub(any(Channel.class)))
                    .thenReturn(mockBlockingStub);
            mockStaticService.when(() -> ServiceGrpc.newStub(any()))
                    .thenReturn(mockStub);

            try (MockedStatic<NettyChannelBuilder> mockStaticChannelBuilder = mockStatic(NettyChannelBuilder.class)) {

                try (MockedConstruction<EpollEventLoopGroup> mockEpollEventLoopGroup = mockConstruction(
                        EpollEventLoopGroup.class,
                        (mock, context) -> {
                        })) {
                    when(NettyChannelBuilder.forAddress(any(DomainSocketAddress.class))).thenReturn(mockChannelBuilder);

                    new GrpcConnector(FlagdOptions.builder().socketPath(path).build(), null, null);

                    // verify path matches
                    mockStaticChannelBuilder.verify(() -> NettyChannelBuilder
                            .forAddress(argThat((DomainSocketAddress d) -> {
                                assertEquals(d.path(), path); // path should match
                                return true;
                            })), times(1));
                }
            }
        }
    }

    @Test
    void no_args_socket_env_should_build_domain_socket_with_correct_path() throws Exception {
        final String path = "/some/other/path";

        new EnvironmentVariables("FLAGD_SOCKET_PATH", path).execute(() -> {

            ServiceGrpc.ServiceBlockingStub mockBlockingStub = mock(ServiceGrpc.ServiceBlockingStub.class);
            ServiceGrpc.ServiceStub mockStub = mock(ServiceGrpc.ServiceStub.class);
            NettyChannelBuilder mockChannelBuilder = getMockChannelBuilderSocket();

            try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
                mockStaticService.when(() -> ServiceGrpc.newBlockingStub(any(Channel.class)))
                        .thenReturn(mockBlockingStub);
                mockStaticService.when(() -> ServiceGrpc.newStub(any()))
                        .thenReturn(mockStub);

                try (MockedStatic<NettyChannelBuilder> mockStaticChannelBuilder = mockStatic(
                        NettyChannelBuilder.class)) {

                    try (MockedConstruction<EpollEventLoopGroup> mockEpollEventLoopGroup = mockConstruction(
                            EpollEventLoopGroup.class,
                            (mock, context) -> {
                            })) {
                        mockStaticChannelBuilder.when(() -> NettyChannelBuilder
                                .forAddress(any(DomainSocketAddress.class))).thenReturn(mockChannelBuilder);

                        new GrpcConnector(FlagdOptions.builder().build(), null, null);

                        //verify path matches & called times(= 1 as we rely on reusable channel)
                        mockStaticChannelBuilder.verify(() -> NettyChannelBuilder
                                .forAddress(argThat((DomainSocketAddress d) -> {
                                    return d.path() == path;
                                })), times(1));
                    }
                }
            }
        });
    }

    @Test
    void host_and_port_arg_should_build_tcp_socket() {
        final String host = "host.com";
        final int port = 1234;

        ServiceGrpc.ServiceBlockingStub mockBlockingStub = mock(ServiceGrpc.ServiceBlockingStub.class);
        ServiceGrpc.ServiceStub mockStub = mock(ServiceGrpc.ServiceStub.class);
        NettyChannelBuilder mockChannelBuilder = getMockChannelBuilderSocket();

        try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
            mockStaticService.when(() -> ServiceGrpc.newBlockingStub(any(Channel.class)))
                    .thenReturn(mockBlockingStub);
            mockStaticService.when(() -> ServiceGrpc.newStub(any()))
                    .thenReturn(mockStub);

            try (MockedStatic<NettyChannelBuilder> mockStaticChannelBuilder = mockStatic(NettyChannelBuilder.class)) {

                mockStaticChannelBuilder.when(() -> NettyChannelBuilder
                        .forAddress(anyString(), anyInt())).thenReturn(mockChannelBuilder);

                final FlagdOptions flagdOptions = FlagdOptions.builder().host(host).port(port).tls(false).build();
                new GrpcConnector(flagdOptions, null, null);

                // verify host/port matches
                mockStaticChannelBuilder.verify(() -> NettyChannelBuilder
                        .forAddress(host, port), times(1));
            }
        }
    }

    @Test
    void no_args_host_and_port_env_set_should_build_tcp_socket() throws Exception {
        final String host = "server.com";
        final int port = 4321;

        new EnvironmentVariables("FLAGD_HOST", host, "FLAGD_PORT", String.valueOf(port)).execute(() -> {
            ServiceGrpc.ServiceBlockingStub mockBlockingStub = mock(ServiceGrpc.ServiceBlockingStub.class);
            ServiceGrpc.ServiceStub mockStub = mock(ServiceGrpc.ServiceStub.class);
            NettyChannelBuilder mockChannelBuilder = getMockChannelBuilderSocket();

            try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
                mockStaticService.when(() -> ServiceGrpc.newBlockingStub(any(Channel.class)))
                        .thenReturn(mockBlockingStub);
                mockStaticService.when(() -> ServiceGrpc.newStub(any()))
                        .thenReturn(mockStub);

                try (MockedStatic<NettyChannelBuilder> mockStaticChannelBuilder = mockStatic(
                        NettyChannelBuilder.class)) {

                    mockStaticChannelBuilder.when(() -> NettyChannelBuilder
                            .forAddress(anyString(), anyInt())).thenReturn(mockChannelBuilder);

                    new GrpcConnector(FlagdOptions.builder().build(), null, null);

                    // verify host/port matches & called times(= 1 as we rely on reusable channel)
                    mockStaticChannelBuilder.verify(() -> NettyChannelBuilder.
                            forAddress(host, port), times(1));
                }
            }
        });
    }

    private NettyChannelBuilder getMockChannelBuilderSocket() {
        NettyChannelBuilder mockChannelBuilder = mock(NettyChannelBuilder.class);
        when(mockChannelBuilder.eventLoopGroup(any(EventLoopGroup.class))).thenReturn(mockChannelBuilder);
        when(mockChannelBuilder.channelType(any(Class.class))).thenReturn(mockChannelBuilder);
        when(mockChannelBuilder.usePlaintext()).thenReturn(mockChannelBuilder);
        when(mockChannelBuilder.build()).thenReturn(null);
        return mockChannelBuilder;
    }
}
