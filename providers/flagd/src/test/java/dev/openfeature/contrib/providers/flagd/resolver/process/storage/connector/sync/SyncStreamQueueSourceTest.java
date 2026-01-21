package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelConnector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc.FlagSyncServiceBlockingStub;
import dev.openfeature.flagd.grpc.sync.FlagSyncServiceGrpc.FlagSyncServiceStub;
import dev.openfeature.flagd.grpc.sync.Sync.GetMetadataResponse;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsRequest;
import dev.openfeature.flagd.grpc.sync.Sync.SyncFlagsResponse;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class SyncStreamQueueSourceTest {

    private ChannelConnector mockConnector;
    private FlagSyncServiceBlockingStub blockingStub;
    private FlagSyncServiceStub stub;
    private FlagSyncServiceStub syncErrorStub;
    private FlagSyncServiceStub asyncErrorStub;
    private StreamObserver<SyncFlagsResponse> observer;
    private CountDownLatch latch; // used to wait for observer to be initialized
    private SyncStreamQueueSource queueSource;

    @BeforeEach
    @SuppressWarnings("deprecation")
    public void setup() throws Exception {
        blockingStub = mock(FlagSyncServiceBlockingStub.class);
        when(blockingStub.withDeadlineAfter(anyLong(), any())).thenReturn(blockingStub);
        when(blockingStub.getMetadata(any())).thenReturn(GetMetadataResponse.getDefaultInstance());

        mockConnector = mock(ChannelConnector.class);

        stub = mock(FlagSyncServiceStub.class);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        doAnswer((Answer<Void>) invocation -> {
                    Object[] args = invocation.getArguments();
                    @SuppressWarnings("unchecked")
                    StreamObserver<SyncFlagsResponse> obs = (StreamObserver<SyncFlagsResponse>) args[1];
                    observer = obs;
                    latch.countDown();
                    return null;
                })
                .when(stub)
                .syncFlags(any(SyncFlagsRequest.class), any()); // mock the initialize

        syncErrorStub = mock(FlagSyncServiceStub.class);
        when(syncErrorStub.withDeadlineAfter(anyLong(), any())).thenReturn(syncErrorStub);
        doAnswer((Answer<Void>) invocation -> {
                    Object[] args = invocation.getArguments();
                    @SuppressWarnings("unchecked")
                    StreamObserver<SyncFlagsResponse> obs = (StreamObserver<SyncFlagsResponse>) args[1];
                    observer = obs;
                    latch.countDown();
                    throw new StatusRuntimeException(io.grpc.Status.NOT_FOUND);
                })
                .when(syncErrorStub)
                .syncFlags(any(SyncFlagsRequest.class), any()); // mock the initialize

        asyncErrorStub = mock(FlagSyncServiceStub.class);
        when(asyncErrorStub.withDeadlineAfter(anyLong(), any())).thenReturn(asyncErrorStub);
        doAnswer((Answer<Void>) invocation -> {
                    Object[] args = invocation.getArguments();
                    @SuppressWarnings("unchecked")
                    StreamObserver<SyncFlagsResponse> obs = (StreamObserver<SyncFlagsResponse>) args[1];
                    observer = obs;
                    latch.countDown();

                    // start a thread to call onError after a short delay
                    new Thread(() -> {
                                try {
                                    Thread.sleep(10); // wait 10ms before calling onError
                                    observer.onError(new StatusRuntimeException(io.grpc.Status.INTERNAL));
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            })
                            .start();

                    return null;
                })
                .when(asyncErrorStub)
                .syncFlags(any(SyncFlagsRequest.class), any()); // mock the initialize
    }

    @AfterEach
    public void tearDown() throws Exception {
        queueSource.shutdown();
    }

    @Test
    void reinitializeChannelComponents_reinitializesWhenEnabled() throws InterruptedException {
        FlagdOptions options = FlagdOptions.builder().reinitializeOnError(true).build();
        ChannelConnector initialConnector = mock(ChannelConnector.class);
        FlagSyncServiceStub initialStub = mock(FlagSyncServiceStub.class);
        FlagSyncServiceBlockingStub initialBlockingStub = mock(FlagSyncServiceBlockingStub.class);
        queueSource = new SyncStreamQueueSource(options, initialConnector, initialStub, initialBlockingStub);

        try {
            // save reference to old GrpcComponents
            Object oldComponents = getPrivateField(queueSource, "grpcComponents");
            queueSource.reinitializeChannelComponents();
            Object newComponents = getPrivateField(queueSource, "grpcComponents");
            // should have replaced grpcComponents
            assertNotNull(newComponents);
            org.junit.jupiter.api.Assertions.assertNotSame(oldComponents, newComponents);
        } finally {
            queueSource.shutdown();
        }
    }

    @Test
    void reinitializeChannelComponents_doesNothingWhenDisabled() throws InterruptedException {
        FlagdOptions options = FlagdOptions.builder().reinitializeOnError(false).build();
        ChannelConnector initialConnector = mock(ChannelConnector.class);
        FlagSyncServiceStub initialStub = mock(FlagSyncServiceStub.class);
        FlagSyncServiceBlockingStub initialBlockingStub = mock(FlagSyncServiceBlockingStub.class);
        queueSource = new SyncStreamQueueSource(options, initialConnector, initialStub, initialBlockingStub);

        try {
            Object oldComponents = getPrivateField(queueSource, "grpcComponents");
            queueSource.reinitializeChannelComponents();
            Object newComponents = getPrivateField(queueSource, "grpcComponents");
            // should NOT have replaced grpcComponents
            org.junit.jupiter.api.Assertions.assertSame(oldComponents, newComponents);
        } finally {
            queueSource.shutdown();
        }
    }

    @Test
    void reinitializeChannelComponents_doesNothingWhenShutdown() throws InterruptedException {
        FlagdOptions options = FlagdOptions.builder().reinitializeOnError(true).build();
        ChannelConnector initialConnector = mock(ChannelConnector.class);
        FlagSyncServiceStub initialStub = mock(FlagSyncServiceStub.class);
        FlagSyncServiceBlockingStub initialBlockingStub = mock(FlagSyncServiceBlockingStub.class);
        queueSource = new SyncStreamQueueSource(options, initialConnector, initialStub, initialBlockingStub);

        queueSource.shutdown();
        Object oldComponents = getPrivateField(queueSource, "grpcComponents");
        queueSource.reinitializeChannelComponents();
        Object newComponents = getPrivateField(queueSource, "grpcComponents");
        // should NOT have replaced grpcComponents
        org.junit.jupiter.api.Assertions.assertSame(oldComponents, newComponents);
    }
    // helper to access private fields via reflection
    private static Object getPrivateField(Object instance, String fieldName) {
        try {
            java.lang.reflect.Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(instance);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void syncInitError_DoesNotBusyWait() throws Exception {
        // make sure we do not spin in a busy loop on immediately errors

        int maxBackoffMs = 1000;
        queueSource = new SyncStreamQueueSource(
                FlagdOptions.builder().retryBackoffMaxMs(maxBackoffMs).build(),
                mockConnector,
                syncErrorStub,
                blockingStub);
        latch = new CountDownLatch(1);
        queueSource.init();
        latch.await();

        BlockingQueue<QueuePayload> streamQueue = queueSource.getStreamQueue();
        QueuePayload payload = streamQueue.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull(payload);
        assertEquals(QueuePayloadType.ERROR, payload.getType());
        Thread.sleep(maxBackoffMs + (maxBackoffMs / 2)); // wait 1.5x our delay for retries

        // should have retried the stream (2 calls); initial + 1 retry
        // it's very important that the retry count is low, to confirm no busy-loop
        verify(syncErrorStub, times(2)).syncFlags(any(), any());
    }

    @Test
    void asyncInitError_DoesNotBusyWait() throws Exception {
        // make sure we do not spin in a busy loop on async errors

        int maxBackoffMs = 1000;
        queueSource = new SyncStreamQueueSource(
                FlagdOptions.builder().retryBackoffMaxMs(maxBackoffMs).build(),
                mockConnector,
                asyncErrorStub,
                blockingStub);
        latch = new CountDownLatch(1);
        queueSource.init();
        latch.await();

        BlockingQueue<QueuePayload> streamQueue = queueSource.getStreamQueue();
        QueuePayload payload = streamQueue.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull(payload);
        assertEquals(QueuePayloadType.ERROR, payload.getType());
        Thread.sleep(maxBackoffMs + (maxBackoffMs / 2)); // wait 1.5x our delay for retries

        // should have retried the stream (2 calls); initial + 1 retry
        // it's very important that the retry count is low, to confirm no busy-loop
        verify(asyncErrorStub, times(2)).syncFlags(any(), any());
    }

    @Test
    void onNextEnqueuesDataPayload() throws Exception {
        queueSource = new SyncStreamQueueSource(FlagdOptions.builder().build(), mockConnector, stub, blockingStub);
        latch = new CountDownLatch(1);
        queueSource.init();
        latch.await();

        // fire onNext (data) event
        observer.onNext(SyncFlagsResponse.newBuilder().build());

        // should enqueue data payload
        BlockingQueue<QueuePayload> streamQueue = queueSource.getStreamQueue();
        QueuePayload payload = streamQueue.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull(payload);
        assertNotNull(payload.getSyncContext());
        assertEquals(QueuePayloadType.DATA, payload.getType());
        // should NOT have restarted the stream (1 call)
        verify(stub, times(1)).syncFlags(any(), any());
    }

    @Test
    @SuppressWarnings("deprecation")
    void onNextEnqueuesDataPayloadMetadataDisabled() throws Exception {
        // disable GetMetadata call
        queueSource = new SyncStreamQueueSource(
                FlagdOptions.builder().syncMetadataDisabled(true).build(), mockConnector, stub, blockingStub);
        latch = new CountDownLatch(1);
        queueSource.init();
        latch.await();

        // fire onNext (data) event
        observer.onNext(SyncFlagsResponse.newBuilder().build());

        // should enqueue data payload
        BlockingQueue<QueuePayload> streamQueue = queueSource.getStreamQueue();
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
        queueSource = new SyncStreamQueueSource(FlagdOptions.builder().build(), mockConnector, stub, blockingStub);
        latch = new CountDownLatch(1);
        queueSource.init();
        latch.await();

        // fire onNext (data) event
        Struct syncContext = Struct.newBuilder().build();
        observer.onNext(
                SyncFlagsResponse.newBuilder().setSyncContext(syncContext).build());

        // should enqueue data payload
        BlockingQueue<QueuePayload> streamQueue = queueSource.getStreamQueue();
        QueuePayload payload = streamQueue.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull(payload);
        assertEquals(syncContext, payload.getSyncContext());
        assertEquals(QueuePayloadType.DATA, payload.getType());
        // should NOT have restarted the stream (1 call)
        verify(stub, times(1)).syncFlags(any(), any());
    }

    @Test
    void onErrorEnqueuesDataPayload() throws Exception {
        queueSource = new SyncStreamQueueSource(FlagdOptions.builder().build(), mockConnector, stub, blockingStub);
        latch = new CountDownLatch(1);
        queueSource.init();
        latch.await();

        // fire onError event and reset latch
        latch = new CountDownLatch(1);
        observer.onError(new Exception("fake exception"));

        // should enqueue error payload
        BlockingQueue<QueuePayload> streamQueue = queueSource.getStreamQueue();
        QueuePayload payload = streamQueue.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull(payload);
        assertEquals(QueuePayloadType.ERROR, payload.getType());
        // should have restarted the stream (2 calls)
        latch.await();
        verify(stub, times(2)).syncFlags(any(), any());
    }

    @Test
    void onCompletedEnqueuesDataPayload() throws Exception {
        queueSource = new SyncStreamQueueSource(FlagdOptions.builder().build(), mockConnector, stub, blockingStub);
        latch = new CountDownLatch(1);
        queueSource.init();
        latch.await();

        // fire onCompleted event (graceful stream end) and reset latch
        latch = new CountDownLatch(1);
        observer.onCompleted();

        // should enqueue error payload
        BlockingQueue<QueuePayload> streamQueue = queueSource.getStreamQueue();
        assertTrue(streamQueue.isEmpty());
        // should have restarted the stream (2 calls)
        latch.await();
        verify(stub, times(2)).syncFlags(any(), any());
    }
}
