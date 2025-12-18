package dev.openfeature.contrib.providers.flagd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FlagdThreadFactoryTest {

    private static final String THREAD_NAME = "testthread";
    private final Runnable runnable = () -> {};

    @Test
    void verifyThreadFactoryThrowsNullPointerExceptionWhenNamePrefixIsNull() {

        // Then
        var exception = assertThrows(NullPointerException.class, () -> {
            // When
            new FlagdThreadFactory(null);
        });
        assertThat(exception.toString()).contains("namePrefix must not be null");
    }

    @Test
    void verifyNewThreadHasNamePrefix() {

        var flagdThreadFactory = new FlagdThreadFactory(THREAD_NAME);
        var thread = flagdThreadFactory.newThread(runnable);

        assertThat(thread.getName()).isEqualTo(THREAD_NAME + "-1");
        assertThat(thread.isDaemon()).isTrue();
    }

    @Test
    void verifyNewThreadHasNamePrefixWithIncrement() {

        var flagdThreadFactory = new FlagdThreadFactory(THREAD_NAME);
        var threadOne = flagdThreadFactory.newThread(runnable);
        var threadTwo = flagdThreadFactory.newThread(runnable);

        assertThat(threadOne.getName()).isEqualTo(THREAD_NAME + "-1");
        assertThat(threadOne.isDaemon()).isTrue();
        assertThat(threadTwo.getName()).isEqualTo(THREAD_NAME + "-2");
        assertThat(threadTwo.isDaemon()).isTrue();
    }
}
