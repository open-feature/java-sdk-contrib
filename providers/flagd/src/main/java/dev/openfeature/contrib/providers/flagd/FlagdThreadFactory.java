package dev.openfeature.contrib.providers.flagd;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread factory for the Flagd provider to allow named daemon threads to be created.
 */
class FlagdThreadFactory implements ThreadFactory {

    private final AtomicInteger counter = new AtomicInteger();
    private final String namePrefix;

    /**
     * {@link FlagdThreadFactory}'s constructor.
     *
     * @param namePrefix    Prefix used for setting the new thread's name.
     */
    FlagdThreadFactory(String namePrefix) {
        this.namePrefix = Objects.requireNonNull(namePrefix, "namePrefix must not be null");
    }

    @Override
    public Thread newThread(Runnable runnable) {
        final Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setName(namePrefix + "-" + counter.incrementAndGet());
        return thread;
    }
}
