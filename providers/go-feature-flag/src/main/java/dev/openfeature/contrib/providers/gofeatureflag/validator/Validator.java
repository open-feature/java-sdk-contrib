package dev.openfeature.contrib.providers.gofeatureflag.validator;

import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;

/**
 * Validator class is providing utils method to validate the options provided.
 */
public class Validator {
    /**
     * Validate the options provided to the publisher.
     *
     * @param flushIntervalMs  - flush interval in milliseconds
     * @param maxPendingEvents - max pending events
     * @throws InvalidOptions - if options are invalid
     */
    public static void publisherOptions(final Long flushIntervalMs, final Integer maxPendingEvents)
            throws InvalidOptions {
        if (flushIntervalMs != null && flushIntervalMs <= 0) {
            throw new InvalidOptions("flushIntervalMs must be larger than 0");
        }
        if (maxPendingEvents != null && maxPendingEvents <= 0) {
            throw new InvalidOptions("maxPendingEvents must be larger than 0");
        }
    }
}
