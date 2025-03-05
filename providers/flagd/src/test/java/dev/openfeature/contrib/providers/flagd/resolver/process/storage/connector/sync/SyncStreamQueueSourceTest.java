package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync;

import static org.junit.Assert.assertNotNull;
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

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelConnector;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

class SyncStreamQueueSourceTest {
    private ChannelConnector<FlagSyncServiceStub, FlagSyncServiceBlockingStub> mockConnector;
    private FlagSyncServiceBlockingStub blockingStub;
    private FlagSyncServiceStub stub;
    private SyncStreamObserver observer;
    private CountDownLatch latch; // used to wait for observer to be initialized

    @BeforeEach
    public void init() throws Exception {
        blockingStub = mock(FlagSyncServiceBlockingStub.class);
        when(blockingStub.getMetadata(any())).thenReturn(GetMetadataResponse.getDefaultInstance());

        mockConnector = mock(ChannelConnector.class);
        when(mockConnector.getBlockingStub()).thenReturn(blockingStub);
        doNothing().when(mockConnector).initialize(); // Mock the initialize method

        stub = mock(FlagSyncServiceStub.class);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        doAnswer(new Answer<Void>() {
                    public Void answer(InvocationOnMock invocation) {
                        latch.countDown();
                        Object[] args = invocation.getArguments();
                        observer = (SyncStreamObserver) args[1];
                        return null;
                    }
                })
                .when(stub)
                .syncFlags(any(SyncFlagsRequest.class), any(SyncStreamObserver.class)); // Mock the initialize method
    }

    @Test
    void onNextEnqueuesDataPayload() throws Exception {
        SyncStreamQueueSource connector =
                new SyncStreamQueueSource(FlagdOptions.builder().build(), mockConnector, stub);
        connector.init();
        latch = new CountDownLatch(1);
        latch.await();

        // fire onNext (data) event
        observer.onNext(SyncFlagsResponse.newBuilder().build());

        // should enqueue data payload
        BlockingQueue<QueuePayload> streamQueue = connector.getStreamQueue();
        QueuePayload payload = streamQueue.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull(payload);
        assertEquals(QueuePayloadType.DATA, payload.getType());
        // should NOT have restarted the stream (1 call)
        verify(stub, times(1)).syncFlags(any(), any());
    }

    @Test
    void onErrorEnqueuesDataPayload() throws Exception {
        SyncStreamQueueSource connector =
                new SyncStreamQueueSource(FlagdOptions.builder().build(), mockConnector, stub);
        connector.init();
        latch = new CountDownLatch(1);
        latch.await();

        // fire onError event
        observer.onError(new Exception("fake exception"));

        // should enqueue error payload
        BlockingQueue<QueuePayload> streamQueue = connector.getStreamQueue();
        QueuePayload payload = streamQueue.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull(payload);
        assertEquals(QueuePayloadType.ERROR, payload.getType());
        // should have restarted the stream (2 calls)
        verify(stub, times(2)).syncFlags(any(), any());
    }

    @Test
    void onCompletedEnqueuesDataPayload() throws Exception {
        SyncStreamQueueSource connector =
                new SyncStreamQueueSource(FlagdOptions.builder().build(), mockConnector, stub);
        connector.init();
        latch = new CountDownLatch(1);
        latch.await();

        // fire onCompleted event (graceful stream end)
        observer.onCompleted();

        // should enqueue error payload
        BlockingQueue<QueuePayload> streamQueue = connector.getStreamQueue();
        assertTrue(streamQueue.isEmpty());
        // should have restarted the stream (2 calls)
        latch = new CountDownLatch(1);
        latch.await();
        verify(stub, times(2)).syncFlags(any(), any());
    }
}
