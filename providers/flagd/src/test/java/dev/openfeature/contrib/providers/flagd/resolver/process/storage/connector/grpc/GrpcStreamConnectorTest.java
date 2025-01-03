package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.grpc;

import static dev.openfeature.contrib.providers.flagd.resolver.common.Convert.convertProtobufMapToStructure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc.FlagSyncServiceBlockingStub;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc.FlagSyncServiceStub;
import dev.openfeature.flagd.grpc.sync.Sync.GetMetadataResponse;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsRequest;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsResponse;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GrpcStreamConnectorTest {

    private static final Duration MAX_WAIT_MS = Duration.ofMillis(500);

    @Test
    public void connectionParameters() throws Throwable {
        // given
        final FlagdOptions options = FlagdOptions.builder()
                .selector("selector")
                .deadline(1337)
                .streamDeadlineMs(87699)
                .build();

        final GrpcStreamConnector connector = new GrpcStreamConnector(options);
        final FlagSyncServiceStub stubMock = mockStubAndReturn(connector);
        final FlagSyncServiceBlockingStub blockingStubMock = mockBlockingStubAndReturn(connector);
        final SyncFlagsRequest[] request = new SyncFlagsRequest[1];

        doAnswer(invocation -> {
                    request[0] = invocation.getArgument(0, SyncFlagsRequest.class);
                    return null;
                })
                .when(stubMock)
                .syncFlags(any(), any());

        // when
        connector.init();
        verify(stubMock, timeout(MAX_WAIT_MS.toMillis()).times(1)).syncFlags(any(), any());
        verify(blockingStubMock).withDeadlineAfter(1337, TimeUnit.MILLISECONDS);
        verify(stubMock).withDeadlineAfter(87699, TimeUnit.MILLISECONDS);

        // then
        final SyncFlagsRequest flagsRequest = request[0];
        assertNotNull(flagsRequest);
        assertEquals("selector", flagsRequest.getSelector());
    }

    @Test
    public void disableStreamDeadline() throws Throwable {
        // given
        final FlagdOptions options =
                FlagdOptions.builder().selector("selector").streamDeadlineMs(0).build();

        final GrpcStreamConnector connector = new GrpcStreamConnector(options);
        final FlagSyncServiceStub stubMock = mockStubAndReturn(connector);
        final FlagSyncServiceBlockingStub blockingStubMock = mockBlockingStubAndReturn(connector);
        final SyncFlagsRequest[] request = new SyncFlagsRequest[1];

        doAnswer(invocation -> {
                    request[0] = invocation.getArgument(0, SyncFlagsRequest.class);
                    return null;
                })
                .when(stubMock)
                .syncFlags(any(), any());

        // when
        connector.init();
        verify(stubMock, timeout(MAX_WAIT_MS.toMillis()).times(1)).syncFlags(any(), any());
        verify(stubMock, never()).withDeadlineAfter(anyLong(), any());

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
        final GrpcStreamConnector connector =
                new GrpcStreamConnector(FlagdOptions.builder().build());
        final FlagSyncServiceStub stubMock = mockStubAndReturn(connector);
        final FlagSyncServiceBlockingStub blockingStubMock = mockBlockingStubAndReturn(connector);
        final Struct metadata = Struct.newBuilder()
                .putFields(
                        key,
                        com.google.protobuf.Value.newBuilder()
                                .setStringValue(val)
                                .build())
                .build();

        when(blockingStubMock.withDeadlineAfter(anyLong(), any())).thenReturn(blockingStubMock);
        when(blockingStubMock.getMetadata(any()))
                .thenReturn(
                        GetMetadataResponse.newBuilder().setMetadata(metadata).build());

        final GrpcStreamHandler[] injectedHandler = new GrpcStreamHandler[1];

        doAnswer(invocation -> {
                    injectedHandler[0] = invocation.getArgument(1, GrpcStreamHandler.class);
                    return null;
                })
                .when(stubMock)
                .syncFlags(any(), any());

        // when
        connector.init();
        // verify and wait for initialization
        verify(stubMock, timeout(MAX_WAIT_MS.toMillis()).times(1)).syncFlags(any(), any());
        verify(blockingStubMock).getMetadata(any());

        // then
        final GrpcStreamHandler grpcStreamHandler = injectedHandler[0];
        assertNotNull(grpcStreamHandler);

        final BlockingQueue<QueuePayload> streamPayloads = connector.getStream();

        // accepted data
        grpcStreamHandler.onNext(SyncFlagsResponse.newBuilder().build());

        assertTimeoutPreemptively(MAX_WAIT_MS, () -> {
            QueuePayload payload = streamPayloads.take();
            assertEquals(QueuePayloadType.DATA, payload.getType());
            assertEquals(
                    val,
                    convertProtobufMapToStructure(
                                    payload.getMetadataResponse().getMetadata().getFieldsMap())
                            .asObjectMap()
                            .get(key));
        });

        // ping must be ignored
        grpcStreamHandler.onNext(SyncFlagsResponse.newBuilder().build());

        // accepted data
        grpcStreamHandler.onNext(SyncFlagsResponse.newBuilder().build());

        assertTimeoutPreemptively(MAX_WAIT_MS, () -> {
            QueuePayload payload = streamPayloads.take();
            assertEquals(QueuePayloadType.DATA, payload.getType());
        });
    }

    @Test
    public void listenerExitOnShutdown() throws Throwable {
        // given
        final GrpcStreamConnector connector =
                new GrpcStreamConnector(FlagdOptions.builder().build());
        final FlagSyncServiceStub stubMock = mockStubAndReturn(connector);
        final FlagSyncServiceBlockingStub blockingStubMock = mockBlockingStubAndReturn(connector);
        final GrpcStreamHandler[] injectedHandler = new GrpcStreamHandler[1];
        final Struct metadata = Struct.newBuilder().build();

        when(blockingStubMock.withDeadlineAfter(anyLong(), any())).thenReturn(blockingStubMock);
        when(blockingStubMock.getMetadata(any()))
                .thenReturn(
                        GetMetadataResponse.newBuilder().setMetadata(metadata).build());
        when(stubMock.withDeadlineAfter(anyLong(), any())).thenReturn(stubMock);
        doAnswer(invocation -> {
                    injectedHandler[0] = invocation.getArgument(1, GrpcStreamHandler.class);
                    return null;
                })
                .when(stubMock)
                .syncFlags(any(), any());

        // when
        connector.init();
        // verify and wait for initialization
        verify(stubMock, timeout(MAX_WAIT_MS.toMillis()).times(1)).syncFlags(any(), any());
        verify(blockingStubMock).getMetadata(any());

        // then
        final GrpcStreamHandler grpcStreamHandler = injectedHandler[0];
        assertNotNull(grpcStreamHandler);

        // invoke shutdown
        connector.shutdown();
        // mock channel close of gRPC handler
        grpcStreamHandler.onError(new Exception("Channel closed, exiting"));

        // Validate mock calls & no more event propagation
        verify(stubMock, times(1)).syncFlags(any(), any());

        grpcStreamHandler.onNext(SyncFlagsResponse.newBuilder()
                // .setState(SyncService.SyncState.SYNC_STATE_ALL)
                .build());

        // there should be no data
        assertNull(connector.getStream().poll(100, TimeUnit.MILLISECONDS));
    }

    private static FlagSyncServiceStub mockStubAndReturn(final GrpcStreamConnector connector) throws Throwable {
        final Field serviceStubField = GrpcStreamConnector.class.getDeclaredField("serviceStub");
        serviceStubField.setAccessible(true);

        final FlagSyncServiceStub stubMock = mock(FlagSyncServiceStub.class);
        when(stubMock.withDeadlineAfter(anyLong(), any())).thenReturn(stubMock);

        serviceStubField.set(connector, stubMock);

        return stubMock;
    }

    private static FlagSyncServiceBlockingStub mockBlockingStubAndReturn(final GrpcStreamConnector connector)
            throws Throwable {
        final Field blockingStubField = GrpcStreamConnector.class.getDeclaredField("serviceBlockingStub");
        blockingStubField.setAccessible(true);

        final FlagSyncServiceBlockingStub blockingStubMock = mock(FlagSyncServiceBlockingStub.class);

        blockingStubField.set(connector, blockingStubMock);

        return blockingStubMock;
    }
}
