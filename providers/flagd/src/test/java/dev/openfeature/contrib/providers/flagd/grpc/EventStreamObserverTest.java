package dev.openfeature.contrib.providers.flagd.grpc;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.openfeature.contrib.providers.flagd.FlagdCache;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.flagd.grpc.Schema;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderEventDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

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
    class EmitEvents {

        FlagdCache cache;
        FlagdProvider callback;
        EventStreamObserver stream;

        @BeforeEach
        void setUp() {
            cache = mock(FlagdCache.class);
            when(cache.getEnabled()).thenReturn(true);
            callback = mock(FlagdProvider.class);
            doCallRealMethod().when(callback).emitConfigurationChangeEvent();
            doCallRealMethod().when(callback).emitSuccessReconnectionEvents();
            doCallRealMethod().when(callback).emitProviderConfigurationChanged(any(ProviderEventDetails.class));
            doCallRealMethod().when(callback).emitProviderReady(any(ProviderEventDetails.class));
            doCallRealMethod().when(callback).emit(any(ProviderEvent.class), any(ProviderEventDetails.class));
            stream = new EventStreamObserver(cache, callback);
        }

        @Test
        public void Change(){
            Schema.EventStreamResponse resp = mock(Schema.EventStreamResponse.class);
            Struct flagData = mock(Struct.class);
            when(resp.getType()).thenReturn("configuration_change");
            when(resp.getData()).thenReturn(flagData);
            when(flagData.getFieldsMap()).thenReturn(new HashMap<>());
            stream.onNext(resp);
            // we notify that the configuration changed
            verify(callback, times(1)).emitConfigurationChangeEvent();
            verify(callback, times(1)).emitProviderConfigurationChanged(any(ProviderEventDetails.class));
            verify(callback, times(1)).emit(eq(ProviderEvent.PROVIDER_CONFIGURATION_CHANGED), any(ProviderEventDetails.class));
            // we flush the cache
            verify(cache, atLeast(1)).clear();
        }

        @Test
        public void Ready(){
            Schema.EventStreamResponse resp = mock(Schema.EventStreamResponse.class);
            when(resp.getType()).thenReturn("provider_ready");
            stream.onNext(resp);
            verify(callback, times(0)).emit(any(ProviderEvent.class), any(ProviderEventDetails.class));
        }

        @Test
        public void Reconnections(){
            stream.onError(new Throwable("error"));
            // we flush the cache
            verify(cache, atLeast(1)).clear();
            // we emit PROVIDER_READY and PROVIDER_CONFIGURATION_CHANGED
            verify(callback, times(1)).emit(eq(ProviderEvent.PROVIDER_READY), any(ProviderEventDetails.class));
            verify(callback, times(1)).emit(eq(ProviderEvent.PROVIDER_CONFIGURATION_CHANGED), any(ProviderEventDetails.class));
        }

        @Test
        public void NotSuccessReconnections() throws Exception {
            doThrow(new Exception("connection error")).when(callback).restartEventStream();
            stream.onError(new Throwable("error"));
            // we flush the cache
            verify(cache, atLeast(1)).clear();
            // we do NOT emit any events
            verify(callback, times(0)).emit(eq(ProviderEvent.PROVIDER_READY), any(ProviderEventDetails.class));
            verify(callback, times(0)).emit(eq(ProviderEvent.PROVIDER_CONFIGURATION_CHANGED), any(ProviderEventDetails.class));
        }

        @Test
        public void CacheBustingForKnownKeys(){
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
            verify(callback, times(1)).emitConfigurationChangeEvent();
            verify(callback, times(1)).emitProviderConfigurationChanged(any(ProviderEventDetails.class));
            verify(callback, times(1)).emit(eq(ProviderEvent.PROVIDER_CONFIGURATION_CHANGED), any(ProviderEventDetails.class));
            // we did NOT flush the whole cache
            verify(cache, atMost(0)).clear();
            // we only clean the two keys
            verify(cache, times(1)).remove(eq(key1));
            verify(cache, times(1)).remove(eq(key2));
        }
    }
}