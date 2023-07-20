package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.flagd.grpc.Schema;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderEventDetails;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EventObject;

import static org.mockito.Mockito.*;

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
            when(resp.getType()).thenReturn("configuration_change");
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
    }


}