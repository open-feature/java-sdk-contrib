package dev.openfeature.contrib.providers.flagd.resolver.grpc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ConnectionEvent;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.Cache;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.EventStreamResponse;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc.ServiceBlockingStub;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc.ServiceStub;
import dev.openfeature.sdk.internal.TriConsumer;
import io.grpc.Channel;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

public class GrpcConnectorTest {

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3 })
    void validate_retry_calls(int retries) throws NoSuchFieldException, IllegalAccessException {
        final int backoffMs = 100;

        final FlagdOptions options = FlagdOptions.builder()
                // shorter backoff for testing
                .retryBackoffMs(backoffMs)
                .maxEventStreamRetries(retries)
                .build();

        final Cache cache = new Cache("disabled", 0);

        final ServiceGrpc.ServiceStub mockStub = mock(ServiceGrpc.ServiceStub.class);
        doAnswer(invocation -> null).when(mockStub).eventStream(any(), any());

        final GrpcConnector connector = new GrpcConnector(options, cache, () -> true,
                (connectionEvent) -> {
                });

        Field serviceStubField = GrpcConnector.class.getDeclaredField("serviceStub");
        serviceStubField.setAccessible(true);
        serviceStubField.set(connector, mockStub);

        final Object syncObject = new Object();

        Field syncField = GrpcConnector.class.getDeclaredField("sync");
        syncField.setAccessible(true);
        syncField.set(connector, syncObject);

        try {
            connector.initialize();
        } catch (Exception e) {
            // ignored - we expect the failure and this test validate retry attempts
        }

        for (int i = 1; i < retries; i++) {
            // verify invocation with enough timeout value
            verify(mockStub, timeout(2L * i * backoffMs).times(i)).eventStream(any(), any());

            synchronized (syncObject) {
                syncObject.notify();
            }
        }
    }

    @Test
    void initialization_succeed_with_connected_status() throws NoSuchFieldException, IllegalAccessException {
        final Cache cache = new Cache("disabled", 0);
        final ServiceGrpc.ServiceStub mockStub = mock(ServiceGrpc.ServiceStub.class);
        Consumer<ConnectionEvent> onConnectionEvent = mock(Consumer.class);
        doAnswer((InvocationOnMock invocation) -> {
            EventStreamObserver eventStreamObserver = (EventStreamObserver) invocation.getArgument(1);
            eventStreamObserver
                    .onNext(EventStreamResponse.newBuilder().setType(EventStreamObserver.PROVIDER_READY).build());
            return null;
        }).when(mockStub).eventStream(any(), any());

        try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
            mockStaticService.when(() -> ServiceGrpc.newStub(any()))
                    .thenReturn(mockStub);

            // pass true in connected lambda
            final GrpcConnector connector = new GrpcConnector(FlagdOptions.builder().build(), cache, () -> {
                try {
                    Thread.sleep(100);
                    return true;
                } catch (Exception e) {
                }
                return false;

            },
                    onConnectionEvent);

            assertDoesNotThrow(connector::initialize);
            // assert that onConnectionEvent is connected
            verify(onConnectionEvent).accept(argThat(arg -> arg.isConnected()));
        }
    }

    @Test
    void initialization_fail_with_timeout() throws Exception {
        final Cache cache = new Cache("disabled", 0);
        final ServiceGrpc.ServiceStub mockStub = mock(ServiceGrpc.ServiceStub.class);
        Consumer<ConnectionEvent> onConnectionEvent = mock(Consumer.class);
        doAnswer(invocation -> null).when(mockStub).eventStream(any(), any());

        final GrpcConnector connector = new GrpcConnector(FlagdOptions.builder().build(), cache, () -> false,
                onConnectionEvent);

        // assert throws
        assertThrows(RuntimeException.class, connector::initialize);
        // assert that onConnectionEvent is not connected
        verify(onConnectionEvent).accept(argThat(arg -> !arg.isConnected()));
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
                new GrpcConnector(flagdOptions, null, null, null);

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

                    new GrpcConnector(FlagdOptions.builder().build(), null, null, null);

                    // verify host/port matches & called times(= 1 as we rely on reusable channel)
                    mockStaticChannelBuilder.verify(() -> NettyChannelBuilder.forAddress(host, port), times(1));
                }
            }
        });
    }

    /**
     * OS Specific test - This test is valid only on Linux system as it rely on
     * epoll availability
     */
    @Test
    @EnabledOnOs(OS.LINUX)
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

                    new GrpcConnector(FlagdOptions.builder().socketPath(path).build(), null, null, null);

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

    /**
     * OS Specific test - This test is valid only on Linux system as it rely on
     * epoll availability
     */
    @Test
    @EnabledOnOs(OS.LINUX)
    void no_args_socket_env_should_build_domain_socket_with_correct_path() throws Exception {
        final String path = "/some/other/path";

        new EnvironmentVariables("FLAGD_SOCKET_PATH", path).execute(() -> {

            ServiceBlockingStub mockBlockingStub = mock(ServiceBlockingStub.class);
            ServiceStub mockStub = mock(ServiceStub.class);
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

                        new GrpcConnector(FlagdOptions.builder().build(), null, null, null);

                        // verify path matches & called times(= 1 as we rely on reusable channel)
                        mockStaticChannelBuilder.verify(() -> NettyChannelBuilder
                                .forAddress(argThat((DomainSocketAddress d) -> {
                                    return d.path() == path;
                                })), times(1));
                    }
                }
            }
        });
    }

    private NettyChannelBuilder getMockChannelBuilderSocket() {
        NettyChannelBuilder mockChannelBuilder = mock(NettyChannelBuilder.class);
        when(mockChannelBuilder.eventLoopGroup(any(EventLoopGroup.class))).thenReturn(mockChannelBuilder);
        when(mockChannelBuilder.channelType(any(Class.class))).thenReturn(mockChannelBuilder);
        when(mockChannelBuilder.usePlaintext()).thenReturn(mockChannelBuilder);
        when(mockChannelBuilder.keepAliveTime(anyLong(), any())).thenReturn(mockChannelBuilder);
        when(mockChannelBuilder.build()).thenReturn(null);
        return mockChannelBuilder;
    }
}
