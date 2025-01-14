package dev.openfeature.contrib.providers.flagd.resolver.common;

import static dev.openfeature.contrib.providers.flagd.resolver.common.ChannelMonitor.monitorChannelState;
import static dev.openfeature.contrib.providers.flagd.resolver.common.ChannelMonitor.waitForDesiredState;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.openfeature.sdk.exceptions.GeneralError;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ChannelMonitorTest {
    @Test
    void testWaitForDesiredState() throws InterruptedException {
        ManagedChannel channel = mock(ManagedChannel.class);
        Runnable connectCallback = mock(Runnable.class);

        // Set up the desired state
        ConnectivityState desiredState = ConnectivityState.READY;
        when(channel.getState(anyBoolean())).thenReturn(desiredState);

        // Call the method
        waitForDesiredState(desiredState, channel, connectCallback, 1, TimeUnit.SECONDS);

        // Verify that the callback was run
        verify(connectCallback, times(1)).run();
    }

    @Test
    void testWaitForDesiredStateTimeout() {
        ManagedChannel channel = Mockito.mock(ManagedChannel.class);
        Runnable connectCallback = mock(Runnable.class);

        // Set up the desired state
        ConnectivityState desiredState = ConnectivityState.READY;
        when(channel.getState(anyBoolean())).thenReturn(ConnectivityState.IDLE);

        // Call the method and expect a timeout
        assertThrows(GeneralError.class, () -> {
            waitForDesiredState(desiredState, channel, connectCallback, 1, TimeUnit.SECONDS);
        });
    }

    @ParameterizedTest
    @EnumSource(ConnectivityState.class)
    void testMonitorChannelState(ConnectivityState state) {
        ManagedChannel channel = Mockito.mock(ManagedChannel.class);
        Runnable onConnectionReady = mock(Runnable.class);
        Runnable onConnectionLost = mock(Runnable.class);

        // Set up the expected state
        ConnectivityState expectedState = ConnectivityState.IDLE;
        when(channel.getState(anyBoolean())).thenReturn(state);

        // Capture the callback
        ArgumentCaptor<Runnable> callbackCaptor = ArgumentCaptor.forClass(Runnable.class);
        doNothing().when(channel).notifyWhenStateChanged(eq(expectedState), callbackCaptor.capture());

        // Call the method
        monitorChannelState(expectedState, channel, onConnectionReady, onConnectionLost);

        // Simulate state change
        callbackCaptor.getValue().run();

        // Verify the callbacks based on the state
        if (state == ConnectivityState.READY) {
            verify(onConnectionReady, times(1)).run();
            verify(onConnectionLost, never()).run();
        } else if (state == ConnectivityState.TRANSIENT_FAILURE || state == ConnectivityState.SHUTDOWN) {
            verify(onConnectionReady, never()).run();
            verify(onConnectionLost, times(1)).run();
        } else {
            verify(onConnectionReady, never()).run();
            verify(onConnectionLost, never()).run();
        }
    }
}
