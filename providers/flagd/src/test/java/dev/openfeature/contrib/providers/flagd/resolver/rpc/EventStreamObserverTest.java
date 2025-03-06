package dev.openfeature.contrib.providers.flagd.resolver.rpc;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.resolver.common.QueueingStreamObserver;
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
        QueueingStreamObserver<Object> stream;
        Runnable reconnect;
        Object sync;
        BlockingQueue<QueueingStreamObserver<Object>> queue = new LinkedBlockingQueue<>(10);

        @BeforeEach
        void setUp() {
            states = new ArrayList<>();
            reconnect = mock(Runnable.class);
            stream = new QueueingStreamObserver(queue);
        }

        @Test
        public void dequeue() throws InterruptedException {
            EventStreamResponse resp = mock(EventStreamResponse.class);
            Struct flagData = mock(Struct.class);
            when(resp.getType()).thenReturn(Constants.CONFIGURATION_CHANGE);
            when(resp.getData()).thenReturn(flagData);
            when(flagData.getFieldsMap()).thenReturn(new HashMap<>());
            stream.onNext(resp);
            // we notify that we are ready
            Object taken = queue.take();
            assertNotNull(taken);
        }
    }
}
