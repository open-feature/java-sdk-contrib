package dev.openfeature.contrib.providers.flagd.resolver.rpc;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelConnector;
import dev.openfeature.contrib.providers.flagd.resolver.common.QueueingStreamObserver;
import dev.openfeature.flagd.grpc.evaluation.v2.Evaluation.EventStreamResponse;
import dev.openfeature.flagd.grpc.evaluation.v2.ServiceGrpc.ServiceBlockingStub;
import dev.openfeature.flagd.grpc.evaluation.v2.ServiceGrpc.ServiceStub;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.internal.TriConsumer;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

class RpcResolverTest {
    private ChannelConnector mockConnector;
    private ServiceBlockingStub blockingStub;
    private ServiceStub stub;
    private ServiceStub errorStub;
    private QueueingStreamObserver<EventStreamResponse> observer;
    private TriConsumer<ProviderEvent, ProviderEventDetails, Structure> consumer;
    private CountDownLatch latch; // used to wait for observer to be initialized

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void init() throws Exception {
        latch = new CountDownLatch(1);
        observer = null;
        consumer = mock(TriConsumer.class);
        doNothing().when(consumer).accept(any(), any(), any());

        blockingStub = mock(ServiceBlockingStub.class);

        mockConnector = mock(ChannelConnector.class);

        stub = mock(ServiceStub.class);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        doAnswer(new Answer<Void>() {
                    public Void answer(InvocationOnMock invocation) {
                        Object[] args = invocation.getArguments();
                        if (args[1] != null) {
                            observer = (QueueingStreamObserver<EventStreamResponse>) args[1];
                        }
                        latch.countDown();
                        return null;
                    }
                })
                .when(stub)
                .eventStream(any(), any()); // Mock the initialize method

        // stub that immediately fires onError on every eventStream call
        errorStub = mock(ServiceStub.class);
        when(errorStub.withDeadlineAfter(anyLong(), any())).thenReturn(errorStub);
        doAnswer((Answer<Void>) invocation -> {
                    @SuppressWarnings("unchecked")
                    QueueingStreamObserver<EventStreamResponse> obs = (QueueingStreamObserver<EventStreamResponse>)
                            invocation.getArguments()[1];
                    latch.countDown();
                    // immediately fire error on a separate thread
                    new Thread(() -> {
                                try {
                                    Thread.sleep(10);
                                    obs.onError(new Exception("immediate error"));
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            })
                            .start();
                    return null;
                })
                .when(errorStub)
                .eventStream(any(), any());
    }

    @Test
    void onNextWithReadyRunsConsumerWithReady() throws Exception {
        RpcResolver resolver =
                new RpcResolver(FlagdOptions.builder().build(), null, consumer, stub, blockingStub, mockConnector);
        resolver.init();
        latch.await();

        // fire onNext (data) event
        observer.onNext(EventStreamResponse.newBuilder()
                .setType(Constants.PROVIDER_READY)
                .build());

        // should run consumer with payload
        await().untilAsserted(() -> verify(consumer).accept(eq(ProviderEvent.PROVIDER_READY), any(), any()));
        // should NOT have restarted the stream (1 call)
        verify(stub, times(1)).eventStream(any(), any());
    }

    @Test
    void onNextWithChangedRunsConsumerWithChanged() throws Exception {
        RpcResolver resolver =
                new RpcResolver(FlagdOptions.builder().build(), null, consumer, stub, blockingStub, mockConnector);
        resolver.init();
        latch.await();

        // fire onNext (data) event
        observer.onNext(EventStreamResponse.newBuilder()
                .setType(Constants.CONFIGURATION_CHANGE)
                .build());

        // should run consumer with payload
        verify(stub, times(1)).eventStream(any(), any());
        // should have restarted the stream (2 calls)
        await().untilAsserted(
                        () -> verify(consumer).accept(eq(ProviderEvent.PROVIDER_CONFIGURATION_CHANGED), any(), any()));
    }

    @Test
    void onCompletedRerunsStreamWithError() throws Exception {
        RpcResolver resolver = new RpcResolver(
                FlagdOptions.builder().retryBackoffMaxMs(100).build(),
                null,
                consumer,
                stub,
                blockingStub,
                mockConnector);
        resolver.init();
        latch.await();

        // fire onNext (data) event
        observer.onCompleted();

        // should run consumer with error
        await().untilAsserted(() -> verify(consumer).accept(eq(ProviderEvent.PROVIDER_ERROR), any(), any()));
        // should have restarted the stream (2 calls)
        await().untilAsserted(() -> verify(stub, times(2)).eventStream(any(), any()));
    }

    @Test
    void onErrorRunsConsumerWithError() throws Exception {
        RpcResolver resolver = new RpcResolver(
                FlagdOptions.builder().retryBackoffMaxMs(100).build(),
                null,
                consumer,
                stub,
                blockingStub,
                mockConnector);
        resolver.init();
        latch.await();

        // fire onNext (data) event
        observer.onError(new Exception("fake error"));

        // should run consumer with error
        await().untilAsserted(() -> verify(consumer).accept(eq(ProviderEvent.PROVIDER_ERROR), any(), any()));
        // should have restarted the stream (2 calls)
        await().untilAsserted(() -> verify(stub, times(2)).eventStream(any(), any()));
    }

    @Test
    void onError_RetriesWithNonBlockingBackoff() throws Exception {
        // make sure we do not spin in a busy loop on immediate errors
        int maxBackoffMs = 1000;
        RpcResolver resolver = new RpcResolver(
                FlagdOptions.builder().retryBackoffMaxMs(maxBackoffMs).build(),
                null,
                consumer,
                errorStub,
                blockingStub,
                mockConnector);
        resolver.init();
        latch.await();

        // wait 1.5x our delay for retries
        Thread.sleep(maxBackoffMs + (maxBackoffMs / 2));

        // should have retried the stream (2 calls); initial + 1 retry
        // it's very important that the retry count is low, to confirm no busy-loop
        verify(errorStub, times(2)).eventStream(any(), any());
    }

    @Test
    void onCompleted_RetriesWithNonBlockingBackoff() throws Exception {
        // make sure we do not spin in a busy loop on stream completion
        int maxBackoffMs = 1000;
        RpcResolver resolver = new RpcResolver(
                FlagdOptions.builder().retryBackoffMaxMs(maxBackoffMs).build(),
                null,
                consumer,
                stub,
                blockingStub,
                mockConnector);
        resolver.init();
        latch.await();

        // fire completion
        observer.onCompleted();

        // wait 1.5x our delay for retries
        Thread.sleep(maxBackoffMs + (maxBackoffMs / 2));

        // should have retried the stream (2 calls); initial + 1 retry
        // it's very important that the retry count is low, to confirm no busy-loop
        verify(stub, times(2)).eventStream(any(), any());
    }
}
