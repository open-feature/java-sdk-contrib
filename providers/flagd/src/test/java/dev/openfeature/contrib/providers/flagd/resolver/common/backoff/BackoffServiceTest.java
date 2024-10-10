package dev.openfeature.contrib.providers.flagd.resolver.common.backoff;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackoffServiceTest {
    @Test
    void getCurrentBackoffMillisReturnsBackoffMillisFromStrategy() {
        BackoffStrategy mockStrategy = mock(BackoffStrategy.class);
        when(mockStrategy.getCurrentBackoffMillis()).thenReturn(1000L);

        BackoffService backoffService = new BackoffService(mockStrategy);

        assertEquals(1000, backoffService.getCurrentBackoffMillis());
        verify(mockStrategy).getCurrentBackoffMillis();
    }

    @Test
    void resetCallsResetOnBackoffStrategy() {
        BackoffStrategy mockStrategy = mock(BackoffStrategy.class);

        BackoffService backoffService = new BackoffService(mockStrategy);
        backoffService.reset();

        verify(mockStrategy).reset();
    }

    @Test
    void waitUntilNextAttemptBlocksForBackoffTimeAndIncreasesBackoff() throws InterruptedException {
        BackoffStrategy mockStrategy = mock(BackoffStrategy.class);
        when(mockStrategy.getCurrentBackoffMillis()).thenReturn(250L);

        BackoffService backoffService = new BackoffService(mockStrategy, 0);

        Instant beforeWait = Instant.now();
        backoffService.waitUntilNextAttempt();
        Instant afterWait = Instant.now();

        long timeElapsed = afterWait.toEpochMilli() - beforeWait.toEpochMilli();
        assertTrue(timeElapsed >= 250);

        verify(mockStrategy).getCurrentBackoffMillis();
        verify(mockStrategy).nextBackoff();
    }

    @Test
    void shouldRetryReturnsTrueIfStrategyIsNotExhausted() {
        BackoffStrategy mockStrategy = mock(BackoffStrategy.class);
        when(mockStrategy.isExhausted()).thenReturn(false);

        BackoffService backoffService = new BackoffService(mockStrategy);

        assertTrue(backoffService.shouldRetry());
        verify(mockStrategy).isExhausted();
    }

    @Test
    void shouldRetryReturnsFalseIfStrategyIsExhausted() {
        BackoffStrategy mockStrategy = mock(BackoffStrategy.class);
        when(mockStrategy.isExhausted()).thenReturn(true);

        BackoffService backoffService = new BackoffService(mockStrategy);

        assertFalse(backoffService.shouldRetry());
        verify(mockStrategy).isExhausted();
    }
}