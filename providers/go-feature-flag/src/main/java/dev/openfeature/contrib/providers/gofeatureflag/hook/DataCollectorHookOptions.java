package dev.openfeature.contrib.providers.gofeatureflag.hook;

import dev.openfeature.contrib.providers.gofeatureflag.bean.IEvent;
import dev.openfeature.contrib.providers.gofeatureflag.service.EvaluationService;
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
     * evalService is the service to evaluate the flags.
     */
    private EvaluationService evalService;
}
