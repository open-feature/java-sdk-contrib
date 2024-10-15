package dev.openfeature.contrib.providers.flagd.resolver.grpc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
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
import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

class GrpcConnectorTest {

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3 })
    void validate_retry_calls(int retries) throws Exception {
        final int backoffMs = 100;

        final FlagdOptions options = FlagdOptions.builder()
                // shorter backoff for testing
                .retryBackoffMs(backoffMs)
                .maxEventStreamRetries(retries)
                .build();

        final Cache cache = new Cache("disabled", 0);

        final ServiceGrpc.ServiceStub mockStub = createServiceStubMock();
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

        assertDoesNotThrow(connector::initialize);

        for (int i = 1; i < retries; i++) {
            // verify invocation with enough timeout value
            verify(mockStub, timeout(2L * i * backoffMs).times(i)).eventStream(any(), any());

            synchronized (syncObject) {
                syncObject.notify();
            }
        }
    }

    @Test
    void initialization_succeed_with_connected_status() {
        final Cache cache = new Cache("disabled", 0);
        final ServiceGrpc.ServiceStub mockStub = createServiceStubMock();
        Consumer<ConnectionEvent> onConnectionEvent = mock(Consumer.class);
        doAnswer((InvocationOnMock invocation) -> {
            EventStreamObserver eventStreamObserver = (EventStreamObserver) invocation.getArgument(1);
            eventStreamObserver
                    .onNext(EventStreamResponse.newBuilder().setType(Constants.PROVIDER_READY).build());
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
    void stream_does_not_fail_on_first_error() {
        final Cache cache = new Cache("disabled", 0);
        final ServiceStub mockStub = createServiceStubMock();
        Consumer<ConnectionEvent> onConnectionEvent = mock(Consumer.class);
        doAnswer((InvocationOnMock invocation) -> {
            EventStreamObserver eventStreamObserver = (EventStreamObserver) invocation.getArgument(1);
            eventStreamObserver
                    .onError(new Exception("fake"));
            return null;
        }).when(mockStub).eventStream(any(), any());

        try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
            mockStaticService.when(() -> ServiceGrpc.newStub(any()))
                    .thenReturn(mockStub);

            // pass true in connected lambda
            final GrpcConnector connector = new GrpcConnector(FlagdOptions.builder().build(), cache,
                    () -> {
                try {
                    Thread.sleep(100);
                    return true;
                } catch (Exception e) {
                }
                return false;

            },
                    onConnectionEvent);

            assertDoesNotThrow(connector::initialize);
            // assert that onConnectionEvent is connected gets not called
            verify(onConnectionEvent, timeout(300).times(0)).accept(any());
        }
    }

    @Test
    void stream_fails_on_second_error_in_a_row() throws Exception {
        final FlagdOptions options = FlagdOptions.builder()
                // shorter backoff for testing
                .retryBackoffMs(0)
                .build();

        final Cache cache = new Cache("disabled", 0);
        Consumer<ConnectionEvent> onConnectionEvent = mock(Consumer.class);

        final ServiceGrpc.ServiceStub mockStub = createServiceStubMock();
        doAnswer((InvocationOnMock invocation) -> {
            EventStreamObserver eventStreamObserver = (EventStreamObserver) invocation.getArgument(1);
            eventStreamObserver
                    .onError(new Exception("fake"));
            return null;
        }).when(mockStub).eventStream(any(), any());

        final GrpcConnector connector = new GrpcConnector(options, cache, () -> true, onConnectionEvent);

        Field serviceStubField = GrpcConnector.class.getDeclaredField("serviceStub");
        serviceStubField.setAccessible(true);
        serviceStubField.set(connector, mockStub);

        final Object syncObject = new Object();

        Field syncField = GrpcConnector.class.getDeclaredField("sync");
        syncField.setAccessible(true);
        syncField.set(connector, syncObject);

        assertDoesNotThrow(connector::initialize);

        // 1st try
        verify(mockStub, timeout(300).times(1)).eventStream(any(), any());
        verify(onConnectionEvent, timeout(300).times(0)).accept(any());
        synchronized (syncObject) {
            syncObject.notify();
        }

        // 2nd try
        verify(mockStub, timeout(300).times(2)).eventStream(any(), any());
        verify(onConnectionEvent, timeout(300).times(1)).accept(argThat(arg -> !arg.isConnected()));

    }

    @Test
    void stream_does_not_fail_when_message_between_errors() throws Exception {
        final FlagdOptions options = FlagdOptions.builder()
                // shorter backoff for testing
                .retryBackoffMs(0)
                .build();

        final Cache cache = new Cache("disabled", 0);
        Consumer<ConnectionEvent> onConnectionEvent = mock(Consumer.class);

        final AtomicBoolean successMessage = new AtomicBoolean(false);
        final ServiceGrpc.ServiceStub mockStub = createServiceStubMock();
        doAnswer((InvocationOnMock invocation) -> {
            EventStreamObserver eventStreamObserver = (EventStreamObserver) invocation.getArgument(1);

            if (successMessage.get()) {
                eventStreamObserver
                        .onNext(EventStreamResponse.newBuilder().setType(Constants.PROVIDER_READY).build());
            } else {
                eventStreamObserver
                        .onError(new Exception("fake"));
            }
            return null;
        }).when(mockStub).eventStream(any(), any());

        final GrpcConnector connector = new GrpcConnector(options, cache, () -> true, onConnectionEvent);

        Field serviceStubField = GrpcConnector.class.getDeclaredField("serviceStub");
        serviceStubField.setAccessible(true);
        serviceStubField.set(connector, mockStub);

        final Object syncObject = new Object();

        Field syncField = GrpcConnector.class.getDeclaredField("sync");
        syncField.setAccessible(true);
        syncField.set(connector, syncObject);

        assertDoesNotThrow(connector::initialize);

        // 1st message with error
        verify(mockStub, timeout(300).times(1)).eventStream(any(), any());
        verify(onConnectionEvent, timeout(300).times(0)).accept(any());

        synchronized (syncObject) {
            successMessage.set(true);
            syncObject.notify();
        }

        // 2nd message with provider ready
        verify(mockStub, timeout(300).times(2)).eventStream(any(), any());
        verify(onConnectionEvent, timeout(300).times(1)).accept(argThat(arg -> arg.isConnected()));
        synchronized (syncObject) {
            successMessage.set(false);
            syncObject.notify();
        }


        // 3nd message with error
        verify(mockStub, timeout(300).times(2)).eventStream(any(), any());
        verify(onConnectionEvent, timeout(300).times(0)).accept(argThat(arg -> !arg.isConnected()));
    }

    @Test
    void stream_does_not_fail_with_deadline_error() throws Exception {
        final Cache cache = new Cache("disabled", 0);
        final ServiceStub mockStub = createServiceStubMock();
        Consumer<ConnectionEvent> onConnectionEvent = mock(Consumer.class);
        doAnswer((InvocationOnMock invocation) -> {
            EventStreamObserver eventStreamObserver = (EventStreamObserver) invocation.getArgument(1);
            eventStreamObserver
                    .onError(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));
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
            // this should not call the connection event
            verify(onConnectionEvent, never()).accept(any());
        }
    }

    @Test
    void host_and_port_arg_should_build_tcp_socket() {
        final String host = "host.com";
        final int port = 1234;

        ServiceGrpc.ServiceBlockingStub mockBlockingStub = mock(ServiceGrpc.ServiceBlockingStub.class);
        ServiceGrpc.ServiceStub mockStub = createServiceStubMock();
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
            ServiceGrpc.ServiceStub mockStub = createServiceStubMock();
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
        ServiceGrpc.ServiceStub mockStub = createServiceStubMock();
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

    @Test
    void initialization_with_stream_deadline() throws NoSuchFieldException, IllegalAccessException {
        final FlagdOptions options = FlagdOptions.builder()
                .streamDeadlineMs(16983)
                .build();

        final Cache cache = new Cache("disabled", 0);
        final ServiceGrpc.ServiceStub mockStub = createServiceStubMock();

        try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
            mockStaticService.when(() -> ServiceGrpc.newStub(any())).thenReturn(mockStub);

            final GrpcConnector connector = new GrpcConnector(options, cache, () -> true, null);

            assertDoesNotThrow(connector::initialize);
            verify(mockStub).withDeadlineAfter(16983, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void initialization_without_stream_deadline() throws NoSuchFieldException, IllegalAccessException {
        final FlagdOptions options = FlagdOptions.builder()
                .streamDeadlineMs(0)
                .build();

        final Cache cache = new Cache("disabled", 0);
        final ServiceGrpc.ServiceStub mockStub = createServiceStubMock();

        try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
            mockStaticService.when(() -> ServiceGrpc.newStub(any())).thenReturn(mockStub);

            final GrpcConnector connector = new GrpcConnector(options, cache, () -> true, null);

            assertDoesNotThrow(connector::initialize);
            verify(mockStub, never()).withDeadlineAfter(16983, TimeUnit.MILLISECONDS);
        }
    }

    private static ServiceStub createServiceStubMock() {
        final ServiceStub mockStub = mock(ServiceStub.class);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(mockStub);
        return mockStub;
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
