package dev.openfeature.contrib.providers.flagd.resolver.grpc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;

import dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.Cache;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.EventStreamResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

class EventStreamObserverTest {

    @Nested
    class StateChange {

        Cache cache;
        List<Boolean> states;
        EventStreamObserver stream;
        Runnable reconnect;
        Object sync;
        Supplier<Boolean> shouldRetrySilently;

        @BeforeEach
        void setUp() {
            states = new ArrayList<>();
            sync = new Object();
            cache = mock(Cache.class);
            reconnect = mock(Runnable.class);
            when(cache.getEnabled()).thenReturn(true);
            shouldRetrySilently = mock(Supplier.class);
            when(shouldRetrySilently.get()).thenReturn(true, false); // 1st time we should retry silently, subsequent calls should not
            stream = new EventStreamObserver(sync, cache, (state, changed) -> states.add(state), shouldRetrySilently);
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
            assertEquals(1, states.size());
            assertTrue(states.get(0));
            // we flush the cache
            verify(cache, atLeast(1)).clear();
        }

        @Test
        public void ready() {
            EventStreamResponse resp = mock(EventStreamResponse.class);
            when(resp.getType()).thenReturn("provider_ready");
            stream.onNext(resp);
            // we notify that we are ready
            assertEquals(1, states.size());
            assertTrue(states.get(0));
            // cache was cleaned
            verify(cache, atLeast(1)).clear();
        }

        @Test
        public void noReconnectionOnFirstError() {
            stream.onError(new Throwable("error"));
            // we flush the cache
            verify(cache, never()).clear();
            // we notify the error
            assertEquals(0, states.size());
        }

        @Test
        public void reconnections() {
            stream.onError(new Throwable("error 1"));
            stream.onError(new Throwable("error 2"));
            // we flush the cache
            verify(cache, atLeast(1)).clear();
            // we notify the error
            assertEquals(1, states.size());
            assertFalse(states.get(0));
        }

        @Test
        public void deadlineExceeded() {
            stream.onError(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));
            // we flush the cache
            verify(cache, never()).clear();
            // we notify the error
            assertEquals(0, states.size());
        }

        @Test
        public void cacheBustingForKnownKeys() {
            final String key1 = "myKey1";
            final String key2 = "myKey2";

            EventStreamResponse resp = mock(EventStreamResponse.class);
            Struct flagData = mock(Struct.class);
            Value flagsValue = mock(Value.class);
            Struct flagsStruct = mock(Struct.class);
            HashMap<String, Value> fields = new HashMap<>();
            fields.put(Constants.FLAGS_KEY, flagsValue);
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
            assertTrue(states.get(0));
            // we did NOT flush the whole cache
            verify(cache, atMost(0)).clear();
            // we only clean the two keys
            verify(cache, times(1)).remove(eq(key1));
            verify(cache, times(1)).remove(eq(key2));
        }
    }
}