package dev.openfeature.contrib.providers.flagd.resolver.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.flagd.grpc.evaluation.Evaluation;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

class ChannelConnectorTest {

    private ManagedChannel testChannel;
    private Server testServer;
    private static final boolean CONNECTED = true;
    private static final boolean DISCONNECTED = false;

    private final ServiceGrpc.ServiceImplBase testServiceImpl = new ServiceGrpc.ServiceImplBase() {
        @Override
        public void eventStream(
                Evaluation.EventStreamRequest request,
                StreamObserver<Evaluation.EventStreamResponse> responseObserver) {
            // noop
        }
    };

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        setupTestGrpcServer();
    }

    private void setupTestGrpcServer() throws IOException {
        var testSocket = new ServerSocket(0);
        var port = testSocket.getLocalPort();
        testSocket.close();

        testServer =
                NettyServerBuilder.forPort(port).addService(testServiceImpl).build();
        testServer.start();

        if (testChannel == null) {
            testChannel = ManagedChannelBuilder.forAddress("localhost", port)
                    .usePlaintext()
                    .build();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        tearDownGrpcServer();
    }

    private void tearDownGrpcServer() throws InterruptedException {
        if (testServer != null) {
            testServer.shutdownNow();
            testServer.awaitTermination();
        }
    }

    @Test
    void whenShuttingDownGrpcConnectorConsumerReceivesDisconnectedEvent() throws Exception {
        CountDownLatch sync = new CountDownLatch(1);
        ArrayList<Boolean> connectionStateChanges = Lists.newArrayList();
        Consumer<FlagdProviderEvent> testConsumer = event -> {
            connectionStateChanges.add(!event.isDisconnected());
            sync.countDown();
        };

        ChannelConnector<ServiceGrpc.ServiceStub, ServiceGrpc.ServiceBlockingStub> instance =
                new ChannelConnector<>(FlagdOptions.builder().build(), testConsumer, testChannel);

        instance.initialize();
        // when shutting grpc connector
        instance.shutdown();

        // then consumer received DISCONNECTED and CONNECTED event
        boolean finished = sync.await(10, TimeUnit.SECONDS);
        Assertions.assertTrue(finished);
        Assertions.assertEquals(Lists.newArrayList(DISCONNECTED), connectionStateChanges);
    }

    @ParameterizedTest
    @EnumSource(ConnectivityState.class)
    void testMonitorChannelState(ConnectivityState state) throws Exception {
        ManagedChannel channel = mock(ManagedChannel.class);

        // Set up the expected state
        ConnectivityState expectedState = ConnectivityState.IDLE;
        when(channel.getState(anyBoolean())).thenReturn(state);

        // Capture the callback
        ArgumentCaptor<Runnable> callbackCaptor = ArgumentCaptor.forClass(Runnable.class);
        doNothing().when(channel).notifyWhenStateChanged(any(), callbackCaptor.capture());

        Consumer<FlagdProviderEvent> testConsumer = spy(Consumer.class);

        ChannelConnector<ServiceGrpc.ServiceStub, ServiceGrpc.ServiceBlockingStub> instance =
                new ChannelConnector<>(FlagdOptions.builder().build(), testConsumer, channel);

        instance.initialize();

        // Simulate state change
        callbackCaptor.getValue().run();

        // Verify the callbacks based on the state
        switch (state) {
            case READY:
                verify(channel, times(2)).notifyWhenStateChanged(any(), any());
                verify(testConsumer, never()).accept(any());
                break;
            case TRANSIENT_FAILURE:
                verify(channel, times(2)).notifyWhenStateChanged(any(), any());
                verify(testConsumer).accept(any());
                break;
            case SHUTDOWN:
                verify(channel, times(1)).notifyWhenStateChanged(any(), any());
                verify(testConsumer).accept(any());
                break;
            default:
                verify(channel, times(2)).notifyWhenStateChanged(any(), any());
                verify(testConsumer, never()).accept(any());
                break;
        }
    }
}
