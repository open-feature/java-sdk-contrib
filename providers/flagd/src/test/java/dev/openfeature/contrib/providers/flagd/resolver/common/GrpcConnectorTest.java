package dev.openfeature.contrib.providers.flagd.resolver.common;

import com.google.common.collect.Lists;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.flagd.grpc.evaluation.Evaluation;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class GrpcConnectorTest {

    private ManagedChannel testChannel;
    private Server testServer;
    private static final boolean CONNECTED = true;
    private static final boolean DISCONNECTED = false;

    @Mock
    private StreamObserver mockEventStreamObserver;

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
        testServer =
                NettyServerBuilder.forPort(8080).addService(testServiceImpl).build();
        testServer.start();

        if (testChannel == null) {
            testChannel = ManagedChannelBuilder.forAddress("localhost", 8080)
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
    void whenShuttingDownGrpcConnector_ConsumerReceivesDisconnectedEvent() throws Exception {
        CountDownLatch sync = new CountDownLatch(1);
        ArrayList<Boolean> connectionStateChanges = Lists.newArrayList();
        Consumer<FlagdProviderEvent> testConsumer = event -> {
            connectionStateChanges.add(!event.isDisconnected());
            sync.countDown();
        };

        GrpcConnector<ServiceGrpc.ServiceStub, ServiceGrpc.ServiceBlockingStub> instance = new GrpcConnector<>(
                FlagdOptions.builder().build(),
                ServiceGrpc::newStub,
                ServiceGrpc::newBlockingStub,
                testConsumer,
                stub -> stub.eventStream(Evaluation.EventStreamRequest.getDefaultInstance(), mockEventStreamObserver),
                testChannel);

        instance.initialize();
        // when shutting grpc connector
        instance.shutdown();

        // then consumer received DISCONNECTED and CONNECTED event
        boolean finished = sync.await(10, TimeUnit.SECONDS);
        Assertions.assertTrue(finished);
        Assertions.assertEquals(Lists.newArrayList(DISCONNECTED), connectionStateChanges);
    }
}
