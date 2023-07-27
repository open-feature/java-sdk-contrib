package dev.openfeature.contrib.providers.gofeatureflag.events;


import dev.openfeature.contrib.providers.gofeatureflag.concurrent.ConcurrentUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Events publisher.
 * @param <T> event type
 *
 * @author Liran Mendelovich
 */
@Slf4j
public class EventsPublisher<T> {

    private int maxPendingEvents;

    private List<T> eventsList;
    private Consumer<List<T>> publisher;
    private long flushIntervalMs;

    private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private Lock readLock = readWriteLock.readLock();
    private Lock writeLock = readWriteLock.writeLock();

    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    public AtomicBoolean isShutdown = new AtomicBoolean(false);

    /**
     * Constructor.
     * @param publisher events publisher
     * @param flushIntervalMs data flush interval
     */
    public EventsPublisher(Consumer<List<T>> publisher, long flushIntervalMs, int maxPendingEvents) {
        eventsList = new CopyOnWriteArrayList<>();
        this.publisher = publisher;
        this.flushIntervalMs = flushIntervalMs;
        this.maxPendingEvents = maxPendingEvents;
        log.debug("Scheduling events publishing at fixed rate of {} milliseconds", flushIntervalMs);
        scheduledExecutorService.scheduleAtFixedRate(
            this::publish, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Add event for aggregation before publishing.
     * @param event event for adding
     */
    public void add(T event) {
        if (isShutdown.get()) {
            log.error("This object was shut down. Omitting event.");
            return;
        }
        if (eventsList.size() >= maxPendingEvents) {
            log.warn("events collection is full. Omitting event.");
            return;
        }
        readLock.lock();
        try {
            eventsList.add(event);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * publish events.
     * @return count of publish events
     */
    public int publish() {
        int publishedEvents = 0;
        writeLock.lock();
        try {
            if (eventsList.isEmpty()) {
                log.info("Not publishing, no events");
            } else {
                log.info("publishing {} events", eventsList.size());
                publisher.accept(new ArrayList<>(eventsList));
                publishedEvents = eventsList.size();
                eventsList = new CopyOnWriteArrayList<>();
            }
        } catch (Exception e) {
            log.error("Error publishing events", e);
        } finally {
            writeLock.unlock();
        }
        return publishedEvents;
    }

    /**
     * Shutdown.
     */
    public void shutdown() {
        log.info("shutdown");
        try {
            log.info("draining remaining events");
            publish();
        } catch (Exception e) {
            log.error("error publishing events on shutdown", e);
        }
        try {
            ConcurrentUtils.shutdownAndAwaitTermination(scheduledExecutorService, 10);
        } catch (Exception e) {
            log.error("error publishing events on shutdown", e);
        }
    }
}
