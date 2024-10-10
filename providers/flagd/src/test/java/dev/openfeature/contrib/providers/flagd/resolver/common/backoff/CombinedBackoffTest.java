package dev.openfeature.contrib.providers.flagd.resolver.common.backoff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CombinedBackoffTest {
    @Test
    void currentBackoffIsFirstBackoff() {
        BackoffStrategy[] backoffStrategies = new BackoffStrategy[]{
                new ConstantTimeBackoff(1),
                new ConstantTimeBackoff(2)
        };

        CombinedBackoff target = new CombinedBackoff(backoffStrategies);

        assertSame(backoffStrategies[0], target.getCurrentStrategy());
    }

    @Test
    void currentBackoffIsFirstNonExhaustedBackoff() {
        BackoffStrategy exhaustedBackoff = mock(BackoffStrategy.class);
        when(exhaustedBackoff.isExhausted()).thenReturn(true);

        BackoffStrategy[] backoffStrategies = new BackoffStrategy[]{
                exhaustedBackoff,
                exhaustedBackoff,
                new ConstantTimeBackoff(3)
        };

        CombinedBackoff target = new CombinedBackoff(backoffStrategies);

        assertSame(backoffStrategies[2], target.getCurrentStrategy());
    }

    @Test
    void currentBackoffIsLastBackoffIfAllBackoffsAreExhausted() {
        BackoffStrategy firstBackoff = mock(BackoffStrategy.class);
        when(firstBackoff.isExhausted()).thenReturn(true);

        BackoffStrategy secondBackoff = mock(BackoffStrategy.class);
        when(secondBackoff.isExhausted()).thenReturn(true);

        BackoffStrategy[] backoffStrategies = new BackoffStrategy[]{
                firstBackoff,
                secondBackoff
        };

        CombinedBackoff target = new CombinedBackoff(backoffStrategies);

        assertSame(backoffStrategies[1], target.getCurrentStrategy());
    }

    @Test
    void currentBackoffSwitchesToNextBackoffWhenExhausted() {
        BackoffStrategy[] backoffStrategies = new BackoffStrategy[]{
                new NumberOfRetriesBackoff(2, new ConstantTimeBackoff(1)),
                new ConstantTimeBackoff(2)
        };

        CombinedBackoff target = new CombinedBackoff(backoffStrategies);

        target.nextBackoff();
        assertEquals(backoffStrategies[0], target.getCurrentStrategy());
        assertEquals(1, target.getCurrentBackoffMillis());

        target.nextBackoff();
        assertEquals(backoffStrategies[0], target.getCurrentStrategy());
        assertEquals(1, target.getCurrentBackoffMillis());

        target.nextBackoff();
        assertEquals(backoffStrategies[1], target.getCurrentStrategy());
        assertEquals(2, target.getCurrentBackoffMillis());
    }

    @Test
    void isExhaustedIsTrueAfterAllBackoffsAreExhausted() {
        BackoffStrategy[] backoffStrategies = new BackoffStrategy[]{
                new NumberOfRetriesBackoff(2, new ConstantTimeBackoff(1)),
                new NumberOfRetriesBackoff(2, new ConstantTimeBackoff(2))
        };

        CombinedBackoff target = new CombinedBackoff(backoffStrategies);

        // Backoff 1
        assertFalse(target.isExhausted());

        target.nextBackoff();
        assertFalse(target.isExhausted());

        target.nextBackoff();
        assertFalse(target.isExhausted());

        // Backoff 2
        target.nextBackoff();
        assertFalse(target.isExhausted());

        target.nextBackoff();
        assertTrue(target.isExhausted());
    }

    @Test
    void resetCallsResetOnAllUsedBackoffsAndswitchesBacktoFirstBackoff() {
        BackoffStrategy firstBackoff = mock(BackoffStrategy.class);
        BackoffStrategy secondBackoff = mock(BackoffStrategy.class);
        BackoffStrategy thirdBackoff = mock(BackoffStrategy.class);

        BackoffStrategy[] backoffStrategies = new BackoffStrategy[]{
                new NumberOfRetriesBackoff(1, firstBackoff),
                new NumberOfRetriesBackoff(1, secondBackoff),
                new NumberOfRetriesBackoff(1, thirdBackoff)
        };

        CombinedBackoff target = new CombinedBackoff(backoffStrategies);

        target.nextBackoff(); // Backoff 1
        target.nextBackoff(); // Backoff 2
        assertEquals(backoffStrategies[1], target.getCurrentStrategy());

        target.reset();

        assertEquals(backoffStrategies[0], target.getCurrentStrategy());

        verify(firstBackoff).reset();
        verify(secondBackoff).reset();
        verify(thirdBackoff, never()).reset();
    }
}