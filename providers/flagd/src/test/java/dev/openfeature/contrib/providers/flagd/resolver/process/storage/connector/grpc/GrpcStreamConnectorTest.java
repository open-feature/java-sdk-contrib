package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayloadType;
import dev.openfeature.flagd.sync.FlagSyncServiceGrpc;
import dev.openfeature.flagd.sync.SyncService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GrpcStreamConnectorTest {

    private static final Duration MAX_WAIT_MS = Duration.ofMillis(500);

    @Test
    public void grpcConnectionStatus() throws Throwable {
        final GrpcStreamConnector connector = new GrpcStreamConnector(FlagdOptions.builder().build());
        final FlagSyncServiceGrpc.FlagSyncServiceStub stubMock = mockStubAndReturn(connector);

        final GrpcStreamHandler[] injectedHandler = new GrpcStreamHandler[1];

        Mockito.doAnswer(invocation -> {
            injectedHandler[0] = invocation.getArgument(1, GrpcStreamHandler.class);
            return null;
        }).when(stubMock).syncFlags(any(), any());

        connector.init();

        // verify and wait for initialization
        verify(stubMock, Mockito.timeout(MAX_WAIT_MS.toMillis()).times(1)).syncFlags(any(), any());

        final GrpcStreamHandler grpcStreamHandler = injectedHandler[0];
        assertNotNull(grpcStreamHandler);

        final BlockingQueue<StreamPayload> streamPayloads = connector.getStream();

        // accepted data
        grpcStreamHandler.onNext(
                SyncService.SyncFlagsResponse.newBuilder()
                        .setState(SyncService.SyncState.SYNC_STATE_ALL)
                        .build());

        assertTimeoutPreemptively(MAX_WAIT_MS, ()->{
            StreamPayload payload = streamPayloads.take();
            assertEquals(StreamPayloadType.DATA, payload.getType());
        });

        // ping must be ignored
        grpcStreamHandler.onNext(
                SyncService.SyncFlagsResponse.newBuilder()
                        .setState(SyncService.SyncState.SYNC_STATE_PING)
                        .build());

        // accepted data
        grpcStreamHandler.onNext(
                SyncService.SyncFlagsResponse.newBuilder()
                        .setState(SyncService.SyncState.SYNC_STATE_ALL)
                        .build());

        assertTimeoutPreemptively(MAX_WAIT_MS, ()->{
            StreamPayload payload = streamPayloads.take();
            assertEquals(StreamPayloadType.DATA, payload.getType());
        });
    }

    @Test
    public void listenerExitOnShutdown() throws Throwable {
        final GrpcStreamConnector connector = new GrpcStreamConnector(FlagdOptions.builder().build());
        final FlagSyncServiceGrpc.FlagSyncServiceStub stubMock = mockStubAndReturn(connector);

        final GrpcStreamHandler[] injectedHandler = new GrpcStreamHandler[1];

        Mockito.doAnswer(invocation -> {
            injectedHandler[0] = invocation.getArgument(1, GrpcStreamHandler.class);
            return null;
        }).when(stubMock).syncFlags(any(), any());

        connector.init();

        // verify and wait for initialization
        verify(stubMock, Mockito.timeout(MAX_WAIT_MS.toMillis()).times(1)).syncFlags(any(), any());

        final GrpcStreamHandler grpcStreamHandler = injectedHandler[0];
        // here
        assertNotNull(grpcStreamHandler);

        // invoke shutdown
        connector.shutdown();
        // mock channel close of gRPC handler
        grpcStreamHandler.onError(new Exception("Channel closed, exiting"));

        assertTimeoutPreemptively(MAX_WAIT_MS, ()->{
            StreamPayload payload = connector.getStream().take();
            assertEquals(StreamPayloadType.ERROR, payload.getType());
        });

        // Validate mock calls & no more event propagation

        verify(stubMock, times(1)).syncFlags(any(), any());

        grpcStreamHandler.onNext(
                SyncService.SyncFlagsResponse.newBuilder()
                        .setState(SyncService.SyncState.SYNC_STATE_ALL)
                        .build());

        // there should be no data
        assertNull(connector.getStream().poll(100, TimeUnit.MILLISECONDS));
    }

    private static FlagSyncServiceGrpc.FlagSyncServiceStub mockStubAndReturn(final GrpcStreamConnector connector) throws Throwable {
        final Field serviceStubField = GrpcStreamConnector.class.getDeclaredField("serviceStub");
        serviceStubField.setAccessible(true);

        final FlagSyncServiceGrpc.FlagSyncServiceStub stubMock =
                Mockito.mock(FlagSyncServiceGrpc.FlagSyncServiceStub.class);

        serviceStubField.set(connector, stubMock);

        return stubMock;
    }

}
