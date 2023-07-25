package dev.openfeature.contrib.providers.gofeatureflag.events;


import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
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

    private List<T> eventsList;
    private Consumer<List<T>> publisher;
    private long flushIntervalMinues;

    private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private Lock readLock = readWriteLock.readLock();
    private Lock writeLock = readWriteLock.writeLock();

    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    /**
     * Constructor.
     * @param publisher events publisher
     * @param flushIntervalMinues data flush interval
     */
    public EventsPublisher(Consumer<List<T>> publisher, long flushIntervalMinues) {
        eventsList = new CopyOnWriteArrayList<>();
        this.publisher = publisher;
        this.flushIntervalMinues = flushIntervalMinues;
        log.debug("Scheduling events publishing at fixed rate of {} minutes", flushIntervalMinues);
        scheduledExecutorService.scheduleAtFixedRate(
            this::publish, flushIntervalMinues, flushIntervalMinues, TimeUnit.MINUTES);
    }

    /**
     * Add event for aggregation before publishing.
     * @param event event for adding
     */
    public void add(T event) {
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
                publishedEvents = eventsList.size();
                publisher.accept(new ArrayList<>(eventsList));
                eventsList = new CopyOnWriteArrayList<>();
            }
        } catch (Exception e) {
            log.error("Error publishing events", e);
        } finally {
            writeLock.unlock();
        }
        return publishedEvents;
    }
}
