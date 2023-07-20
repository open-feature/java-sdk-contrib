package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.flagd.grpc.Schema;
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

        ProviderEventDetails configurationChange = ProviderEventDetails.builder().message("configuration changed").build();
        ProviderEventDetails successReconnection = ProviderEventDetails.builder().message("reconnection successful").build();

        @BeforeEach
        void setUp() {
            cache = mock(FlagdCache.class);
            callback = mock(FlagdProvider.class);
            doCallRealMethod().when(callback).emitConfigurationChangeEvent();
            doCallRealMethod().when(callback).emitSuccessReconnectionEvents();
            stream = new EventStreamObserver(cache, callback);
        }

        @Test
        public void Change(){
            Schema.EventStreamResponse resp = mock(Schema.EventStreamResponse.class);
            when(resp.getType()).thenReturn("configuration_change");
            stream.onNext(resp);
            verify(callback, times(1)).emitProviderConfigurationChanged(null);
        }
        @Test
        public void Ready(){

        }
        @Test
        public void Reconnections(){

        }
    }


}