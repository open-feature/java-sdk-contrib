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
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Events publisher.
 *
 * @param <T> event type
 * @author Liran Mendelovich
 */
@Slf4j
public class EventsPublisher<T> {
    public final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final int maxPendingEvents;
    private final Consumer<List<T>> publisher;

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();

    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
    private List<T> eventsList;

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
        readLock.lock();
        val shouldPublish = (eventsList != null) && (eventsList.size() >= maxPendingEvents);
        readLock.unlock();

        if (shouldPublish) {
            log.warn("events collection is full. Publishing before adding new events.");
            publish();
        }
        writeLock.lock();
        eventsList.add(event);
        writeLock.unlock();
    }

    /**
     * publish events.
     *
     * @return count of publish events
     */
    public int publish() {
        int publishedEvents = 0;
        writeLock.lock();
        try {
            if (eventsList.isEmpty()) {
                log.debug("Not publishing, no events");
                return publishedEvents;
            }
            log.info("publishing {} events", eventsList.size());
            publisher.accept(new ArrayList<>(eventsList));
            publishedEvents = eventsList.size();
            eventsList.clear();
            return publishedEvents;
        } catch (Exception e) {
            log.error("Error publishing events", e);
            return 0;
        } finally {
            writeLock.unlock();
        }
    }

    /** Shutdown. */
    public void shutdown() {
        log.info("shutdown, draining remaining events");
        publish();
        ConcurrentUtil.shutdownAndAwaitTermination(scheduledExecutorService, 10);
    }
}
