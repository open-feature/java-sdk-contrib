package dev.openfeature.contrib.providers.flagd.resolver.common;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/** Queue utils. */
@Slf4j
public class Queues {

    /**
     * The size of flagd blocking queues.
     */
    public static final int QUEUE_SIZE = 5;

    /**
     * Discard the oldest message if the queue is full.
     * This is useful for outbound queues where we don't want to block the producer,
     * as in the case of stream retries.
     *
     * @param queue the queue to check
     * @param <T> the type of the queue
     * @throws InterruptedException if the poll is interrupted
     */
    public static <T> void discardOldestIfFull(BlockingQueue<T> queue) throws InterruptedException {
        // make sure were aren't overloading the queue
        // it's OK if we drop some messages if we're being overwhelmed, it's probably
        // just errors from retries
        if (queue.remainingCapacity() == 0) {
            // poll does not throw and only blocks for the specified duration
            final T dropped = queue.poll(0, TimeUnit.MILLISECONDS);
            log.debug("Outbound queue full, dropping message {}", dropped.toString());
        }
    }
}
