package dev.openfeature.contrib.providers.flagd.resolver.common;

import dev.openfeature.flagd.grpc.evaluation.Evaluation;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
}
