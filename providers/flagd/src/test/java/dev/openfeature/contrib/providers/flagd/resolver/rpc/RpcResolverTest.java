package dev.openfeature.contrib.providers.flagd.resolver.rpc;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelConnector;
import dev.openfeature.contrib.providers.flagd.resolver.common.FlagdProviderEvent;
import dev.openfeature.contrib.providers.flagd.resolver.common.QueueingStreamObserver;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.EventStreamResponse;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc.ServiceBlockingStub;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc.ServiceStub;
import dev.openfeature.sdk.ProviderEvent;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

class RpcResolverTest {
    private ChannelConnector<ServiceStub, ServiceBlockingStub> mockConnector;
    private ServiceBlockingStub blockingStub;
    private ServiceStub stub;
    private QueueingStreamObserver<EventStreamResponse> observer;
    private Consumer<FlagdProviderEvent> consumer;
    private CountDownLatch latch; // used to wait for observer to be initialized

    @BeforeEach
    public void init() throws Exception {
        latch = new CountDownLatch(1);
        observer = null;
        consumer = mock(Consumer.class);
        doNothing().when(consumer).accept(any());

        blockingStub = mock(ServiceBlockingStub.class);

        mockConnector = mock(ChannelConnector.class);
        doNothing().when(mockConnector).initialize(); // Mock the initialize method

        stub = mock(ServiceStub.class);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        doAnswer(new Answer<Void>() {
                    public Void answer(InvocationOnMock invocation) {
                        latch.countDown();
                        Object[] args = invocation.getArguments();
                        if (args[1] != null) {
                            observer = (QueueingStreamObserver<EventStreamResponse>) args[1];
                        }
                        return null;
                    }
                })
                .when(stub)
                .eventStream(any(), any()); // Mock the initialize method
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
        await().untilAsserted(() ->
                verify(consumer).accept(argThat((arg) -> arg.getEvent() == ProviderEvent.PROVIDER_READY)));
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
        await().untilAsserted(() -> verify(consumer)
                .accept(argThat((arg) -> arg.getEvent() == ProviderEvent.PROVIDER_CONFIGURATION_CHANGED)));
    }

    @Test
    void onCompletedRerunsStreamWithError() throws Exception {
        RpcResolver resolver =
                new RpcResolver(FlagdOptions.builder().build(), null, consumer, stub, blockingStub, mockConnector);
        resolver.init();
        latch.await();

        // fire onNext (data) event
        observer.onCompleted();

        // should run consumer with error
        await().untilAsserted(() ->
                verify(consumer).accept(argThat((arg) -> arg.getEvent() == ProviderEvent.PROVIDER_ERROR)));
        // should have restarted the stream (2 calls)
        await().untilAsserted(() -> verify(stub, times(2)).eventStream(any(), any()));
    }

    @Test
    void onErrorRunsConsumerWithError() throws Exception {
        RpcResolver resolver =
                new RpcResolver(FlagdOptions.builder().build(), null, consumer, stub, blockingStub, mockConnector);
        resolver.init();
        latch.await();

        // fire onNext (data) event
        observer.onError(new Exception("fake error"));

        // should run consumer with error
        await().untilAsserted(() ->
                verify(consumer).accept(argThat((arg) -> arg.getEvent() == ProviderEvent.PROVIDER_ERROR)));
        // should have restarted the stream (2 calls)
        await().untilAsserted(() -> verify(stub, times(2)).eventStream(any(), any()));
    }
}
