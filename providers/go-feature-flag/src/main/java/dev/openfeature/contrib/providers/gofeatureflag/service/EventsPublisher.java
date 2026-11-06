package dev.openfeature.contrib.providers.gofeatureflag.service;

import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.util.ConcurrentUtil;
import dev.openfeature.contrib.providers.gofeatureflag.validator.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Events publisher.
 *
 * @param <T> event type
 * @author Liran Mendelovich
 */
@Slf4j
public final class EventsPublisher<T> {
    public final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final int maxPendingEvents;
    private final Consumer<List<T>> publisher;
    /** true while a batch is being posted, so that a second publish skips rather than overlapping it. */
    private final AtomicBoolean publishing = new AtomicBoolean(false);
    /** true while a flush of a full buffer is queued on the scheduler and has not started yet. */
    private final AtomicBoolean flushRequested = new AtomicBoolean(false);

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();

    private final long flushIntervalMs;
    private final List<T> eventsList;
    private volatile ScheduledExecutorService scheduledExecutorService;

    /**
     * Constructor.
     *
     * @param publisher       events publisher
     * @param flushIntervalMs data flush interval
     */
    public EventsPublisher(Consumer<List<T>> publisher, long flushIntervalMs, int maxPendingEvents)
            throws InvalidOptions {
        Validator.publisherOptions(flushIntervalMs, maxPendingEvents);
        eventsList = new CopyOnWriteArrayList<>();
        this.publisher = publisher;
        this.maxPendingEvents = maxPendingEvents;
        this.flushIntervalMs = flushIntervalMs;
        start();
    }

    /**
     * start schedules the periodic flush.
     * Calling start() on a running publisher does nothing, so it is safe to call from both the
     * constructor and provider initialization.
     */
    public synchronized void start() {
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            return;
        }
        isShutdown.set(false);
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        log.debug("Scheduling events publishing at fixed rate of {} milliseconds", flushIntervalMs);
        scheduledExecutorService.scheduleAtFixedRate(
                this::publish, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Add event for aggregation before publishing.
     *
     * @param event event for adding
     */
    public void add(T event) {
        log.debug("Adding event to events collection {}", event);
        if (isShutdown.get()) {
            log.error("This object was shut down. Omitting event.");
            return;
        }

        var shouldPublish = false;
        try {
            readLock.lock();
            shouldPublish = (eventsList != null) && (eventsList.size() >= maxPendingEvents);
        } finally {
            readLock.unlock();
        }

        if (shouldPublish) {
            requestFlush();
        }

        try {
            writeLock.lock();
            if (eventsList != null) {
                eventsList.add(event);
                discardOverflow();
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * requestFlush has the scheduler's thread publish the buffer, so the thread adding an event, often
     * one evaluating a flag, never waits for the data collector. Requests made while one is already
     * queued are merged into it.
     */
    private void requestFlush() {
        if (!flushRequested.compareAndSet(false, true)) {
            return;
        }
        log.warn("events collection is full, publishing it");
        try {
            scheduledExecutorService.execute(() -> {
                flushRequested.set(false);
                publish();
            });
        } catch (RejectedExecutionException e) {
            flushRequested.set(false);
            log.debug("the publisher is shutting down, the final drain will publish the buffer");
        }
    }

    /**
     * discardOverflow keeps the buffer within twice maxPendingEvents, dropping the oldest events
     * first. Without a cap a data collector outage is an unbounded memory leak, and the oldest
     * events are the least useful to keep.
     *
     * <p>Callers must hold {@link #writeLock}.</p>
     */
    private void discardOverflow() {
        long overflow = eventsList.size() - (2L * maxPendingEvents);
        if (overflow > 0) {
            log.warn("events buffer is full, discarding the {} oldest events", overflow);
            eventsList.subList(0, (int) overflow).clear();
        }
    }

    /**
     * publish events.
     *
     * @return count of publish events
     */
    public int publish() {
        if (!publishing.compareAndSet(false, true)) {
            log.debug("a publish is already in progress, skipping this one");
            return 0;
        }
        try {
            return drainAndPost();
        } finally {
            publishing.set(false);
        }
    }

    /**
     * drainAndPost swaps the buffer out under the lock, releases it, and only then posts, so the
     * data collector's availability cannot hold up an evaluation. A batch that fails to publish goes
     * back to the head of the buffer, keeping the events in chronological order.
     *
     * <p>Callers must have set {@link #publishing}, or have stopped the scheduler.</p>
     */
    private int drainAndPost() {
        List<T> batch;
        writeLock.lock();
        try {
            if (eventsList.isEmpty()) {
                log.debug("Not publishing, no events");
                return 0;
            }
            batch = new ArrayList<>(eventsList);
            eventsList.clear();
        } finally {
            writeLock.unlock();
        }

        try {
            log.info("publishing {} events", batch.size());
            publisher.accept(batch);
            return batch.size();
        } catch (Exception e) {
            log.error("Error publishing events", e);
            writeLock.lock();
            try {
                eventsList.addAll(0, batch);
                discardOverflow();
            } finally {
                writeLock.unlock();
            }
            return 0;
        }
    }

    /**
     * Shutdown: stop accepting events, stop the scheduler, letting a publish in progress finish, then
     * drain what is buffered.
     */
    public synchronized void shutdown() {
        log.info("shutdown, draining remaining events");
        isShutdown.set(true);
        if (scheduledExecutorService != null) {
            ConcurrentUtil.shutdownAndAwaitTermination(scheduledExecutorService, 10);
        }
        drainAndPost();
    }
}
