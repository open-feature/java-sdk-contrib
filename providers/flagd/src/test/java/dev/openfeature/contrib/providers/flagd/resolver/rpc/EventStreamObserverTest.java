package dev.openfeature.contrib.providers.flagd.resolver.rpc;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.protobuf.Struct;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.EventStreamResponse;
import dev.openfeature.sdk.ProviderEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EventStreamObserverTest {

    @Nested
    class StateChange {

        List<ProviderEvent> states;
        EventStreamObserver stream;
        Runnable reconnect;
        Object sync;
        BlockingQueue<EventStreamResponseModel> queue = new LinkedBlockingQueue<>(10);

        @BeforeEach
        void setUp() {
            states = new ArrayList<>();
            reconnect = mock(Runnable.class);
            stream = new EventStreamObserver(queue);
        }

        @Test
        public void change() throws InterruptedException {
            EventStreamResponse resp = mock(EventStreamResponse.class);
            Struct flagData = mock(Struct.class);
            when(resp.getType()).thenReturn(Constants.CONFIGURATION_CHANGE);
            when(resp.getData()).thenReturn(flagData);
            when(flagData.getFieldsMap()).thenReturn(new HashMap<>());
            stream.onNext(resp);
            // we notify that we are ready
            EventStreamResponseModel taken = queue.take();
            assertEquals(Constants.CONFIGURATION_CHANGE, taken.getResponse().getType());
        }

        @Test
        public void ready() throws InterruptedException {
            EventStreamResponse resp = mock(EventStreamResponse.class);
            Struct flagData = mock(Struct.class);
            when(resp.getType()).thenReturn(Constants.PROVIDER_READY);
            when(resp.getData()).thenReturn(flagData);
            when(flagData.getFieldsMap()).thenReturn(new HashMap<>());
            stream.onNext(resp);
            // we notify that we are ready
            EventStreamResponseModel taken = queue.take();
            assertEquals(Constants.PROVIDER_READY, taken.getResponse().getType());
        }
    }
}
