package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayloadType;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc.FlagSyncServiceStub;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsRequest;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsResponse;

class GrpcStreamConnectorTest {

    private static final Duration MAX_WAIT_MS = Duration.ofMillis(500);

    @Test
    public void connectionParameters() throws Throwable {
        // given
        final FlagdOptions options = FlagdOptions.builder()
                .selector("selector")
                .build();

        final GrpcStreamConnector connector = new GrpcStreamConnector(options);
        final FlagSyncServiceStub stubMock = mockStubAndReturn(connector);

        final SyncFlagsRequest[] request = new SyncFlagsRequest[1];

        Mockito.doAnswer(invocation -> {
            request[0] = invocation.getArgument(0, SyncFlagsRequest.class);
            return null;
        }).when(stubMock).syncFlags(any(), any());

        // when
        connector.init();
        verify(stubMock, Mockito.timeout(MAX_WAIT_MS.toMillis()).times(1)).syncFlags(any(), any());

        // then
        final SyncFlagsRequest flagsRequest = request[0];
        assertNotNull(flagsRequest);
        assertEquals("selector", flagsRequest.getSelector());
    }

    @Test
    public void grpcConnectionStatus() throws Throwable {
        // given
        final GrpcStreamConnector connector = new GrpcStreamConnector(FlagdOptions.builder().build());
        final FlagSyncServiceStub stubMock = mockStubAndReturn(connector);

        final GrpcStreamHandler[] injectedHandler = new GrpcStreamHandler[1];

        Mockito.doAnswer(invocation -> {
            injectedHandler[0] = invocation.getArgument(1, GrpcStreamHandler.class);
            return null;
        }).when(stubMock).syncFlags(any(), any());

        // when
        connector.init();
        // verify and wait for initialization
        verify(stubMock, Mockito.timeout(MAX_WAIT_MS.toMillis()).times(1)).syncFlags(any(), any());

        // then
        final GrpcStreamHandler grpcStreamHandler = injectedHandler[0];
        assertNotNull(grpcStreamHandler);

        final BlockingQueue<StreamPayload> streamPayloads = connector.getStream();

        // accepted data
        grpcStreamHandler.onNext(
                SyncFlagsResponse.newBuilder()
                        // .setState(SyncState.SYNC_STATE_ALL)
                        .build());

        assertTimeoutPreemptively(MAX_WAIT_MS, () -> {
            StreamPayload payload = streamPayloads.take();
            assertEquals(StreamPayloadType.DATA, payload.getType());
        });

        // ping must be ignored
        grpcStreamHandler.onNext(
                SyncFlagsResponse.newBuilder()
                        // .setState(SyncService.SyncState.SYNC_STATE_PING)
                        .build());

        // accepted data
        grpcStreamHandler.onNext(
                SyncFlagsResponse.newBuilder()
                        // .setState(SyncService.SyncState.SYNC_STATE_ALL) TODO: this and others
                        .build());

        assertTimeoutPreemptively(MAX_WAIT_MS, () -> {
            StreamPayload payload = streamPayloads.take();
            assertEquals(StreamPayloadType.DATA, payload.getType());
        });
    }

    @Test
    public void listenerExitOnShutdown() throws Throwable {
        // given
        final GrpcStreamConnector connector = new GrpcStreamConnector(FlagdOptions.builder().build());
        final FlagSyncServiceStub stubMock = mockStubAndReturn(connector);

        final GrpcStreamHandler[] injectedHandler = new GrpcStreamHandler[1];

        Mockito.doAnswer(invocation -> {
            injectedHandler[0] = invocation.getArgument(1, GrpcStreamHandler.class);
            return null;
        }).when(stubMock).syncFlags(any(), any());

        // when
        connector.init();
        // verify and wait for initialization
        verify(stubMock, Mockito.timeout(MAX_WAIT_MS.toMillis()).times(1)).syncFlags(any(), any());

        // then
        final GrpcStreamHandler grpcStreamHandler = injectedHandler[0];
        assertNotNull(grpcStreamHandler);

        // invoke shutdown
        connector.shutdown();
        // mock channel close of gRPC handler
        grpcStreamHandler.onError(new Exception("Channel closed, exiting"));

        assertTimeoutPreemptively(MAX_WAIT_MS, () -> {
            StreamPayload payload = connector.getStream().take();
            assertEquals(StreamPayloadType.ERROR, payload.getType());
        });

        // Validate mock calls & no more event propagation

        verify(stubMock, times(1)).syncFlags(any(), any());

        grpcStreamHandler.onNext(
                SyncFlagsResponse.newBuilder()
                        // .setState(SyncService.SyncState.SYNC_STATE_ALL)
                        .build());

        // there should be no data
        assertNull(connector.getStream().poll(100, TimeUnit.MILLISECONDS));
    }

    private static FlagSyncServiceStub mockStubAndReturn(final GrpcStreamConnector connector)
            throws Throwable {
        final Field serviceStubField = GrpcStreamConnector.class.getDeclaredField("serviceStub");
        serviceStubField.setAccessible(true);

        final FlagSyncServiceStub stubMock = Mockito.mock(FlagSyncServiceStub.class);

        serviceStubField.set(connector, stubMock);

        return stubMock;
    }

}
