package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc;

import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.protobuf.Struct;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc.FlagSyncServiceBlockingStub;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc.FlagSyncServiceStub;
import dev.openfeature.flagd.grpc.sync.Sync.GetMetadataResponse;
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
        final String key = "key1";
        final String val = "value1";
        final GrpcStreamConnector connector = new GrpcStreamConnector(FlagdOptions.builder().build());
        final FlagSyncServiceStub stubMock = mockStubAndReturn(connector);
        final FlagSyncServiceBlockingStub blockingStubMock = mockBlockingStubAndReturn(connector);

final Struct metadata = Struct.newBuilder()
            .putFields(key,
                    com.google.protobuf.Value.newBuilder().setStringValue(val).build())
            .build();


        when(blockingStubMock.getMetadata(any())).thenReturn(GetMetadataResponse.newBuilder().setMetadata(metadata).build());

        final GrpcStreamHandler[] injectedHandler = new GrpcStreamHandler[1];

        Mockito.doAnswer(invocation -> {
            injectedHandler[0] = invocation.getArgument(1, GrpcStreamHandler.class);
            return null;
        }).when(stubMock).syncFlags(any(), any());

        // when
        connector.init();
        // verify and wait for initialization
        verify(stubMock, Mockito.timeout(MAX_WAIT_MS.toMillis()).times(1)).syncFlags(any(), any());
        verify(blockingStubMock).getMetadata(any());

        // then
        final GrpcStreamHandler grpcStreamHandler = injectedHandler[0];
        assertNotNull(grpcStreamHandler);

        final BlockingQueue<QueuePayload> streamPayloads = connector.getStream();

        // accepted data
        grpcStreamHandler.onNext(
                SyncFlagsResponse.newBuilder()
                        .build());

        assertTimeoutPreemptively(MAX_WAIT_MS, () -> {
            QueuePayload payload = streamPayloads.take();
            assertEquals(QueuePayloadType.DATA, payload.getType());
            assertTrue(() -> payload.getSyncMetadata().get(key).equals(val));
        });

        // ping must be ignored
        grpcStreamHandler.onNext(
                SyncFlagsResponse.newBuilder()
                        .build());

        // accepted data
        grpcStreamHandler.onNext(
                SyncFlagsResponse.newBuilder()
                        .build());

        assertTimeoutPreemptively(MAX_WAIT_MS, () -> {
            QueuePayload payload = streamPayloads.take();
            assertEquals(QueuePayloadType.DATA, payload.getType());
        });
    }

    @Test
    public void listenerExitOnShutdown() throws Throwable {
        // given
        final GrpcStreamConnector connector = new GrpcStreamConnector(FlagdOptions.builder().build());
        final FlagSyncServiceStub stubMock = mockStubAndReturn(connector);
        final FlagSyncServiceBlockingStub blockingStubMock = mockBlockingStubAndReturn(connector);

        final GrpcStreamHandler[] injectedHandler = new GrpcStreamHandler[1];

        Mockito.doAnswer(invocation -> {
            injectedHandler[0] = invocation.getArgument(1, GrpcStreamHandler.class);
            return null;
        }).when(stubMock).syncFlags(any(), any());

        // when
        connector.init();
        // verify and wait for initialization
        verify(stubMock, Mockito.timeout(MAX_WAIT_MS.toMillis()).times(1)).syncFlags(any(), any());
        verify(blockingStubMock).getMetadata(any());

        // then
        final GrpcStreamHandler grpcStreamHandler = injectedHandler[0];
        assertNotNull(grpcStreamHandler);

        // invoke shutdown
        connector.shutdown();
        // mock channel close of gRPC handler
        grpcStreamHandler.onError(new Exception("Channel closed, exiting"));

        assertTimeoutPreemptively(MAX_WAIT_MS, () -> {
            QueuePayload payload = connector.getStream().take();
            assertEquals(QueuePayloadType.ERROR, payload.getType());
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

    private static FlagSyncServiceBlockingStub mockBlockingStubAndReturn(final GrpcStreamConnector connector)
        throws Throwable {
    final Field blockingStubField = GrpcStreamConnector.class.getDeclaredField("serviceBlockingStub");
    blockingStubField.setAccessible(true);

    final FlagSyncServiceBlockingStub blockingStubMock = Mockito.mock(FlagSyncServiceBlockingStub.class);

    blockingStubField.set(connector, blockingStubMock);

    return blockingStubMock;
    }

}
