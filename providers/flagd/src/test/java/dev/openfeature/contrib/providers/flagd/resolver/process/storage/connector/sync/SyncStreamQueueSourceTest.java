package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelConnector;
import dev.openfeature.contrib.providers.flagd.resolver.common.QueueingStreamObserver;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc.FlagSyncServiceBlockingStub;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc.FlagSyncServiceStub;
import dev.openfeature.flagd.grpc.sync.Sync.GetMetadataResponse;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsRequest;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsResponse;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class SyncStreamQueueSourceTest {
    private ChannelConnector<FlagSyncServiceStub, FlagSyncServiceBlockingStub> mockConnector;
    private FlagSyncServiceBlockingStub blockingStub;
    private FlagSyncServiceStub stub;
    private QueueingStreamObserver<SyncFlagsResponse> observer;
    private CountDownLatch latch; // used to wait for observer to be initialized

    @BeforeEach
    public void init() throws Exception {
        blockingStub = mock(FlagSyncServiceBlockingStub.class);
        when(blockingStub.withDeadlineAfter(anyLong(), any())).thenReturn(blockingStub);
        when(blockingStub.getMetadata(any())).thenReturn(GetMetadataResponse.getDefaultInstance());

        mockConnector = mock(ChannelConnector.class);
        doNothing().when(mockConnector).initialize(); // Mock the initialize method

        stub = mock(FlagSyncServiceStub.class);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        doAnswer((Answer<Void>) invocation -> {
                    Object[] args = invocation.getArguments();
                    observer = (QueueingStreamObserver<SyncFlagsResponse>) args[1];
                    latch.countDown();
                    return null;
                })
                .when(stub)
                .syncFlags(any(SyncFlagsRequest.class), any(QueueingStreamObserver.class)); // Mock the initialize
        // method
    }

    @Test
    void onNextEnqueuesDataPayload() throws Exception {
        SyncStreamQueueSource connector =
                new SyncStreamQueueSource(FlagdOptions.builder().build(), mockConnector, stub, blockingStub);
        latch = new CountDownLatch(1);
        connector.init();
        latch.await();

        // fire onNext (data) event
        observer.onNext(SyncFlagsResponse.newBuilder().build());

        // should enqueue data payload
        BlockingQueue<QueuePayload> streamQueue = connector.getStreamQueue();
        QueuePayload payload = streamQueue.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull(payload);
        assertNotNull(payload.getSyncContext());
        assertEquals(QueuePayloadType.DATA, payload.getType());
        // should NOT have restarted the stream (1 call)
        verify(stub, times(1)).syncFlags(any(), any());
    }

    @Test
    void onNextEnqueuesDataPayloadMetadataDisabled() throws Exception {
        // disable GetMetadata call
        SyncStreamQueueSource connector = new SyncStreamQueueSource(
                FlagdOptions.builder().syncMetadataDisabled(true).build(), mockConnector, stub, blockingStub);
        latch = new CountDownLatch(1);
        connector.init();
        latch.await();

        // fire onNext (data) event
        observer.onNext(SyncFlagsResponse.newBuilder().build());

        // should enqueue data payload
        BlockingQueue<QueuePayload> streamQueue = connector.getStreamQueue();
        QueuePayload payload = streamQueue.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull(payload);
        assertNull(payload.getSyncContext());
        assertEquals(QueuePayloadType.DATA, payload.getType());
        // should NOT have restarted the stream (1 call)
        verify(stub, times(1)).syncFlags(any(), any());
        // should NOT have called getMetadata
        verify(blockingStub, times(0)).getMetadata(any());
    }

    @Test
    void onNextEnqueuesDataPayloadWithSyncContext() throws Exception {
        // disable GetMetadata call
        SyncStreamQueueSource connector =
                new SyncStreamQueueSource(FlagdOptions.builder().build(), mockConnector, stub, blockingStub);
        latch = new CountDownLatch(1);
        connector.init();
        latch.await();

        // fire onNext (data) event
        Struct syncContext = Struct.newBuilder().build();
        observer.onNext(
                SyncFlagsResponse.newBuilder().setSyncContext(syncContext).build());

        // should enqueue data payload
        BlockingQueue<QueuePayload> streamQueue = connector.getStreamQueue();
        QueuePayload payload = streamQueue.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull(payload);
        assertEquals(syncContext, payload.getSyncContext());
        assertEquals(QueuePayloadType.DATA, payload.getType());
        // should NOT have restarted the stream (1 call)
        verify(stub, times(1)).syncFlags(any(), any());
    }

    @Test
    void onErrorEnqueuesDataPayload() throws Exception {
        SyncStreamQueueSource connector =
                new SyncStreamQueueSource(FlagdOptions.builder().build(), mockConnector, stub, blockingStub);
        latch = new CountDownLatch(1);
        connector.init();
        latch.await();

        // fire onError event and reset latch
        latch = new CountDownLatch(1);
        observer.onError(new Exception("fake exception"));

        // should enqueue error payload
        BlockingQueue<QueuePayload> streamQueue = connector.getStreamQueue();
        QueuePayload payload = streamQueue.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull(payload);
        assertEquals(QueuePayloadType.ERROR, payload.getType());
        // should have restarted the stream (2 calls)
        latch.await();
        verify(stub, times(2)).syncFlags(any(), any());
    }

    @Test
    void onCompletedEnqueuesDataPayload() throws Exception {
        SyncStreamQueueSource connector =
                new SyncStreamQueueSource(FlagdOptions.builder().build(), mockConnector, stub, blockingStub);
        latch = new CountDownLatch(1);
        connector.init();
        latch.await();

        // fire onCompleted event (graceful stream end) and reset latch
        latch = new CountDownLatch(1);
        observer.onCompleted();

        // should enqueue error payload
        BlockingQueue<QueuePayload> streamQueue = connector.getStreamQueue();
        assertTrue(streamQueue.isEmpty());
        // should have restarted the stream (2 calls)
        latch.await();
        verify(stub, times(2)).syncFlags(any(), any());
    }
}
