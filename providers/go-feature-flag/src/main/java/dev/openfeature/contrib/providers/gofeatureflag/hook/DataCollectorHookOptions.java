package dev.openfeature.contrib.providers.gofeatureflag.hook;

import dev.openfeature.contrib.providers.gofeatureflag.bean.IEvent;
import dev.openfeature.contrib.providers.gofeatureflag.evaluator.IEvaluator;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.service.EventsPublisher;
import lombok.Builder;
import lombok.Getter;

/**
 * DataCollectorHookOptions is the object containing all the options needed for the Data Collector
 * Hook.
 */
@Builder
@Getter
public class DataCollectorHookOptions {
    /**
     * collectUnCachedEvent (optional) set to true if you want to send all events not only the cached
     * evaluations.
     */
    private Boolean collectUnCachedEvaluation;
    /**
     * eventsPublisher is the system collecting all the information to send to GO Feature Flag.
     */
    private EventsPublisher<IEvent> eventsPublisher;

    /**
     * evaluator is used to know whether the usage of a flag should be collected.
     */
    private IEvaluator evaluator;

    /**
     * Validate the options provided to the data collector hook.
     *
     * @throws InvalidOptions - if options are invalid
     */
    public void validate() throws InvalidOptions {
        if (getEventsPublisher() == null) {
            throw new InvalidOptions("No events publisher provided");
        }
    }
}
