package dev.openfeature.contrib.providers.flagd.grpc;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.openfeature.contrib.providers.flagd.FlagdCache;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.flagd.grpc.Schema;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.ProviderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventStreamObserverTest {

    @Nested
    class StateChange {

        FlagdCache cache;
        List<ProviderState> states;
        EventStreamObserver stream;
        Runnable reconnect;

        @BeforeEach
        void setUp() {
            states = new ArrayList<>();
            cache = mock(FlagdCache.class);
            reconnect = mock(Runnable.class);
            when(cache.getEnabled()).thenReturn(true);
            stream = new EventStreamObserver(cache, state -> states.add(state), reconnect);
        }

        @Test
        public void Change() {
            Schema.EventStreamResponse resp = mock(Schema.EventStreamResponse.class);
            Struct flagData = mock(Struct.class);
            when(resp.getType()).thenReturn("configuration_change");
            when(resp.getData()).thenReturn(flagData);
            when(flagData.getFieldsMap()).thenReturn(new HashMap<>());
            stream.onNext(resp);
            // we notify that we are ready
            assertEquals(1, states.size());
            assertEquals(ProviderState.READY, states.get(0));
            // we flush the cache
            verify(cache, atLeast(1)).clear();
        }

        @Test
        public void Ready() {
            Schema.EventStreamResponse resp = mock(Schema.EventStreamResponse.class);
            when(resp.getType()).thenReturn("provider_ready");
            stream.onNext(resp);
            // we notify that we are ready
            assertEquals(1, states.size());
            assertEquals(ProviderState.READY, states.get(0));
            // cache was cleaned
            verify(cache, atLeast(1)).clear();
        }

        @Test
        public void Reconnections() {
            stream.onError(new Throwable("error"));
            // we flush the cache
            verify(cache, atLeast(1)).clear();
            // we notify the error
            assertEquals(1, states.size());
            assertEquals(ProviderState.ERROR, states.get(0));
        }

        @Test
        public void CacheBustingForKnownKeys() {
            final String key1 = "myKey1";
            final String key2 = "myKey2";

            Schema.EventStreamResponse resp = mock(Schema.EventStreamResponse.class);
            Struct flagData = mock(Struct.class);
            Value flagsValue = mock(Value.class);
            Struct flagsStruct = mock(Struct.class);
            HashMap<String, Value> fields = new HashMap<>();
            fields.put(EventStreamObserver.flagsKey, flagsValue);
            HashMap<String, Value> flags = new HashMap<>();
            flags.put(key1, null);
            flags.put(key2, null);

            when(resp.getType()).thenReturn("configuration_change");
            when(resp.getData()).thenReturn(flagData);
            when(flagData.getFieldsMap()).thenReturn(fields);
            when(flagsValue.getStructValue()).thenReturn(flagsStruct);
            when(flagsStruct.getFieldsMap()).thenReturn(flags);

            stream.onNext(resp);
            // we notify that the configuration changed
            assertEquals(1, states.size());
            assertEquals(ProviderState.READY, states.get(0));
            // we did NOT flush the whole cache
            verify(cache, atMost(0)).clear();
            // we only clean the two keys
            verify(cache, times(1)).remove(eq(key1));
            verify(cache, times(1)).remove(eq(key2));
        }
    }
}