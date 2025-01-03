package dev.openfeature.contrib.providers.flagd.resolver.common.backoff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

class NumberOfRetriesBackoffTest {
    @Test
    void currentBackoffMillisIsReadFromInnerBackoff() {
        final long expectedBackoffMillis = 1000;

        BackoffStrategy backoffMock = mock(BackoffStrategy.class);
        when(backoffMock.getCurrentBackoffMillis()).thenReturn(expectedBackoffMillis);

        NumberOfRetriesBackoff target = new NumberOfRetriesBackoff(1, backoffMock);

        long actualBackoffMillis = target.getCurrentBackoffMillis();

        assertEquals(expectedBackoffMillis, actualBackoffMillis);
        verify(backoffMock).getCurrentBackoffMillis();
    }

    @Test
    void initialRetryCountIsZero() {
        BackoffStrategy backoffMock = mock(BackoffStrategy.class);
        NumberOfRetriesBackoff target = new NumberOfRetriesBackoff(1, backoffMock);

        assertEquals(0, target.getRetryCount());
    }

    @Test
    void nextBackoffIncreasesRetryCount() {
        BackoffStrategy backoffMock = mock(BackoffStrategy.class);
        NumberOfRetriesBackoff target = new NumberOfRetriesBackoff(1, backoffMock);

        target.nextBackoff();
        assertEquals(1, target.getRetryCount());
    }

    @Test
    void nextBackoffCallsInnerBackoff() {
        BackoffStrategy backoffMock = mock(BackoffStrategy.class);
        NumberOfRetriesBackoff target = new NumberOfRetriesBackoff(1, backoffMock);

        target.nextBackoff();
        verify(backoffMock).nextBackoff();
    }

    @Test
    void nextBackoffIsNotIncreasedIfRetryCountReached() {
        BackoffStrategy backoffMock = mock(BackoffStrategy.class);
        NumberOfRetriesBackoff target = new NumberOfRetriesBackoff(2, backoffMock);

        target.nextBackoff();
        target.nextBackoff();
        target.nextBackoff();

        assertEquals(2, target.getRetryCount());
    }

    @Test
    void BackoffIsExhaustedIfRetryCountReached() {
        BackoffStrategy backoffMock = mock(BackoffStrategy.class);
        NumberOfRetriesBackoff target = new NumberOfRetriesBackoff(2, backoffMock);

        assertFalse(target.isExhausted());
        target.nextBackoff();

        assertFalse(target.isExhausted());
        target.nextBackoff();

        assertTrue(target.isExhausted());
        target.nextBackoff();

        assertTrue(target.isExhausted());
    }

    @Test
    void nextBackoffDoesNotCallInnerBackoffIfRetryCountReached() {
        BackoffStrategy backoffMock = mock(BackoffStrategy.class);
        NumberOfRetriesBackoff target = new NumberOfRetriesBackoff(2, backoffMock);

        target.nextBackoff();
        target.nextBackoff();
        verify(backoffMock, times(2)).nextBackoff();

        target.nextBackoff();
        verifyNoMoreInteractions(backoffMock);
    }

    @Test
    void resetCallsInnerBackoff() {
        BackoffStrategy backoffMock = mock(BackoffStrategy.class);
        NumberOfRetriesBackoff target = new NumberOfRetriesBackoff(1, backoffMock);

        target.reset();
        verify(backoffMock).reset();
    }

    @Test
    void resetResetsRetryCount() {
        BackoffStrategy backoffMock = mock(BackoffStrategy.class);
        NumberOfRetriesBackoff target = new NumberOfRetriesBackoff(1, backoffMock);

        // increase retry count
        target.nextBackoff();

        target.reset();
        assertEquals(0, target.getRetryCount());
    }
}
