package dev.openfeature.contrib.providers.flagd.resolver.grpc;


import com.google.common.collect.Lists;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ConnectionEvent;
import dev.openfeature.flagd.grpc.evaluation.Evaluation;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

class GrpcConnectorTest {

    private ManagedChannel testChannel;
    private Server testServer;
    private static final boolean CONNECTED = true;
    private static final boolean DISCONNECTED = false;

    @Mock
    private EventStreamObserver mockEventStreamObserver;

    private final ServiceGrpc.ServiceImplBase testServiceImpl = new ServiceGrpc.ServiceImplBase() {
        @Override
        public void eventStream(Evaluation.EventStreamRequest request, StreamObserver<Evaluation.EventStreamResponse> responseObserver) {
            // noop
        }
    };


    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        setupTestGrpcServer();
    }

    private void setupTestGrpcServer() throws IOException {
        String serverName = "test-server";
        testServer = InProcessServerBuilder.forName(serverName)
                .addService(testServiceImpl)
                .directExecutor()
                .build()
                .start();

        testChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
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
    void whenConnectorIsShutdown_ConnectionEventConsumerIsGettingTheEvents() throws Exception {
        CountDownLatch sync = new CountDownLatch(2);
        ArrayList<Boolean> connectionStateChanges = Lists.newArrayList();
        Consumer<ConnectionEvent> testConsumer = event -> {
            connectionStateChanges.add(event.isConnected());
            sync.countDown();

        };
        GrpcConnector<ServiceGrpc.ServiceStub,ServiceGrpc.ServiceBlockingStub> instance = new GrpcConnector<>(FlagdOptions.builder().build(),
                ServiceGrpc::newStub,
                ServiceGrpc::newBlockingStub,
                testConsumer,
                stub -> stub.eventStream(Evaluation.EventStreamRequest.getDefaultInstance(), mockEventStreamObserver),testChannel);

        instance.initialize();
        instance.shutdown();
        boolean finished = sync.await(3, TimeUnit.SECONDS);
        Assertions.assertTrue(finished);
        Assertions.assertEquals(Lists.newArrayList(CONNECTED, DISCONNECTED), connectionStateChanges);
    }


    @Test
    void whenGrpcServerIsRestarted_thenConnectionEventConsumerIsNotified() throws Exception {
        CountDownLatch sync = new CountDownLatch(3);
        ArrayList<Boolean> connectionStateChanges = Lists.newArrayList();
        Consumer<ConnectionEvent> testConsumer = event -> {
            connectionStateChanges.add(event.isConnected());
            sync.countDown();

        };
        GrpcConnector<ServiceGrpc.ServiceStub, ServiceGrpc.ServiceBlockingStub> instance = new GrpcConnector<>(FlagdOptions.builder().build(),
                ServiceGrpc::newStub,
                ServiceGrpc::newBlockingStub,
                testConsumer,
                stub -> stub.eventStream(Evaluation.EventStreamRequest.getDefaultInstance(), mockEventStreamObserver), testChannel);

        instance.initialize();
        tearDownGrpcServer();
        setupTestGrpcServer();

        boolean finished = sync.await(3, TimeUnit.SECONDS);
        Assertions.assertTrue(finished);
        Assertions.assertEquals(Lists.newArrayList(CONNECTED, DISCONNECTED, CONNECTED), connectionStateChanges);
    }


}

