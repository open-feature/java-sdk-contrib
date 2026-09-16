package dev.openfeature.contrib.providers.gofeatureflag.service;

import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.util.ConcurrentUtil;
import dev.openfeature.contrib.providers.gofeatureflag.validator.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
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
    private final Lock publishLock = new ReentrantLock();

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();

    private final long flushIntervalMs;
    private final List<T> eventsList;
    private ScheduledExecutorService scheduledExecutorService;

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
            log.warn("events collection is full. Publishing before adding new events.");
            publish();
        }

        try {
            writeLock.lock();
            if (eventsList != null) {
                eventsList.add(event);
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * publish events.
     *
     * @return count of publish events
     */
    public int publish() {
        if (!publishLock.tryLock()) {
            log.debug("a publish is already in progress, skipping this one");
            return 0;
        }
        try {
            return drainAndPost();
        } finally {
            publishLock.unlock();
        }
    }

    /**
     * drainAndPost swaps the buffer out under the lock, releases it, and only then posts, so the
     * data collector's availability cannot hold up an evaluation. A batch that fails to publish goes
     * back to the head of the buffer, keeping the events in chronological order.
     *
     * <p>Callers must hold {@link #publishLock}.</p>
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
            } finally {
                writeLock.unlock();
            }
            return 0;
        }
    }

    /** Shutdown: stop accepting events, drain what is buffered and stop the scheduler. */
    public synchronized void shutdown() {
        log.info("shutdown, draining remaining events");
        isShutdown.set(true);
        publishLock.lock();
        try {
            drainAndPost();
        } finally {
            publishLock.unlock();
        }
        if (scheduledExecutorService != null) {
            ConcurrentUtil.shutdownAndAwaitTermination(scheduledExecutorService, 10);
        }
    }
}
