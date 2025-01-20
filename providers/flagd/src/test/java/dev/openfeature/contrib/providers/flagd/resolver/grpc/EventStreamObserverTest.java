package dev.openfeature.contrib.providers.flagd.resolver.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.protobuf.Struct;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.EventStreamResponse;
import dev.openfeature.sdk.ProviderEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

        @BeforeEach
        void setUp() {
            states = new ArrayList<>();
            reconnect = mock(Runnable.class);
            stream = new EventStreamObserver(
                    (state) -> states.add(ProviderEvent.PROVIDER_CONFIGURATION_CHANGED),
                    (state) -> states.add(state.getEvent()));
        }

        @Test
        public void change() {
            EventStreamResponse resp = mock(EventStreamResponse.class);
            Struct flagData = mock(Struct.class);
            when(resp.getType()).thenReturn("configuration_change");
            when(resp.getData()).thenReturn(flagData);
            when(flagData.getFieldsMap()).thenReturn(new HashMap<>());
            stream.onNext(resp);
            // we notify that we are ready
            assertThat(states).hasSize(1).contains(ProviderEvent.PROVIDER_CONFIGURATION_CHANGED);
        }
    }
}
